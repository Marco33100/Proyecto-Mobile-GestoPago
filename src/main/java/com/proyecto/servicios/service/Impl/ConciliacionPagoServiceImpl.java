package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.PagoProperties;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.model.pago.ConciliacionDtos.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.ConciliacionPagoService;
import com.proyecto.servicios.support.PaginacionDatos;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Resolucion humana con evidencia y doble control. Nunca envia ventas al proveedor. */
@Service @PreAuthorize("hasRole('EJECUTIVO')")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ConciliacionPagoServiceImpl implements ConciliacionPagoService {
    private final PagoProperties properties;
    private final PagoRepository pagos;
    private final ConciliacionPagoRepository propuestas;
    private final CuentaRepository cuentas;
    private final UserRepository usuarios;
    private final PagoEvidenciaRepository evidencias;
    private final PagoCrypto crypto;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public ConciliacionPagoServiceImpl(PagoProperties properties, PagoRepository pagos, ConciliacionPagoRepository propuestas,
            CuentaRepository cuentas, UserRepository usuarios, PagoEvidenciaRepository evidencias, PagoCrypto crypto,
            ObjectMapper json, @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        this.properties=properties; this.pagos=pagos; this.propuestas=propuestas; this.cuentas=cuentas;
        this.usuarios=usuarios; this.evidencias=evidencias; this.crypto=crypto; this.json=json; tx=new TransactionTemplate(manager);
    }
    private UUID identidad() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser actor)
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_EJECUTIVO".equals(a.getAuthority()))) throw error(HttpStatus.FORBIDDEN);
        var config=properties.getConciliacion();
        if (config==null || !config.isEnabled() || config.getOperadores()==null || !config.getOperadores().contains(actor.userId()))
            throw error(HttpStatus.FORBIDDEN);
        if (config.getOperadores().size()<2 || config.getPropuestaTtl()==null
                || config.getPropuestaTtl().compareTo(Duration.ofMinutes(5))<0
                || config.getPropuestaTtl().compareTo(Duration.ofHours(24))>0) throw error(HttpStatus.SERVICE_UNAVAILABLE);
        return actor.userId();
    }
    private <T> T transaccion(Function<UUID,T> action) {
        UUID actor=identidad();
        return tx.execute(status -> {
            var user=usuarios.bloquearUsuarioDeSesion(actor).orElseThrow(() -> error(HttpStatus.FORBIDDEN));
            if (!user.isEnabled() || user.getRol()!=RolUsuario.EJECUTIVO) throw error(HttpStatus.FORBIDDEN);
            return action.apply(actor);
        });
    }
    @Override public PaginaResponse<Caso> pendientes(PaginacionRequest request) {
        PaginacionDatos.validar(request.getPagina(), request.getTamanio());
        return transaccion(actor -> PaginaResponse.desde(pagos.findByEstado(PagoEntity.Estado.REVISION,
                PageRequest.of(request.getPagina(), request.getTamanio(), Sort.by("enviadoEn").and(Sort.by("id"))))
                .map(p -> new Caso(p.getId(),p.getEstado().name(),p.getMotivoRevision(),p.getUpc(),p.getMonto(),p.getVersion(),p.getEnviadoEn()))));
    }
    @Override public Propuesta proponer(ProponerRequest request) {
        return transaccion(actor -> {
            var pago=pagos.bloquear(request.pagoId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
            var existente=propuestas.findById(request.propuestaId()).orElse(null);
            if (existente!=null) {
                if (!existente.getPropuestoPor().equals(actor) || !detalle(existente).equals(request)) throw error(HttpStatus.CONFLICT);
                return respuesta(existente);
            }
            validarPago(pago,request,actor);
            if (propuestas.existsByPagoIdAndEstado(pago.getId(),ConciliacionPagoEntity.Estado.PROPUESTA)) throw error(HttpStatus.CONFLICT);
            var propuesta=new ConciliacionPagoEntity();
            propuesta.setId(request.propuestaId()); propuesta.setPagoId(pago.getId()); propuesta.setVersionPago(pago.getVersion());
            propuesta.setPropuestoPor(actor); propuesta.setEstado(ConciliacionPagoEntity.Estado.PROPUESTA);
            propuesta.setCreadoEn(Instant.now()); propuesta.setVenceEn(propuesta.getCreadoEn().plus(properties.getConciliacion().getPropuestaTtl()));
            propuesta.setContenidoCifrado(crypto.proteger(serializar(request))); propuestas.saveAndFlush(propuesta);
            evidencia(pago.getId(),"manual-propuesta",Map.of("propuestaId",propuesta.getId(),"actor",actor,"detalle",request));
            return respuesta(propuesta);
        });
    }
    @Override public Propuesta consultar(PropuestaRequest request) {
        return transaccion(actor -> respuesta(propuestas.findById(request.propuestaId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND))));
    }
    @Override public Propuesta aprobar(DecisionRequest request) { return decidir(request,true); }
    @Override public Propuesta rechazar(DecisionRequest request) { return decidir(request,false); }
    private Propuesta decidir(DecisionRequest request,boolean aprobar) {
        return transaccion(actor -> {
            var encontrada=propuestas.findById(request.propuestaId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
            var pago=pagos.bloquear(encontrada.getPagoId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
            var propuesta=propuestas.bloquear(encontrada.getId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
            if (aprobar && propuesta.getPropuestoPor().equals(actor)) throw error(HttpStatus.FORBIDDEN);
            if (propuesta.getEstado()!=ConciliacionPagoEntity.Estado.PROPUESTA) {
                if ((aprobar && propuesta.getEstado()==ConciliacionPagoEntity.Estado.APROBADA)
                        || (!aprobar && propuesta.getEstado()==ConciliacionPagoEntity.Estado.RECHAZADA)) return respuesta(propuesta);
                throw error(HttpStatus.CONFLICT);
            }
            if (aprobar) {
                if (!Instant.now().isBefore(propuesta.getVenceEn())) throw error(HttpStatus.CONFLICT);
                var origen=usuarios.findById(propuesta.getPropuestoPor()).orElseThrow(() -> error(HttpStatus.FORBIDDEN));
                if (!origen.isEnabled() || origen.getRol()!=RolUsuario.EJECUTIVO
                        || !properties.getConciliacion().getOperadores().contains(origen.getId())) throw error(HttpStatus.FORBIDDEN);
                var detalle=detalle(propuesta); validarPago(pago,detalle,actor);
                aplicar(pago,detalle);
            }
            propuesta.setEstado(aprobar?ConciliacionPagoEntity.Estado.APROBADA:ConciliacionPagoEntity.Estado.RECHAZADA);
            propuesta.setDecididoPor(actor); propuesta.setDecididoEn(Instant.now()); propuestas.saveAndFlush(propuesta);
            evidencia(pago.getId(),aprobar?"manual-aprobar":"manual-rechazar",Map.of("propuestaId",propuesta.getId(),"actor",actor,"observacion",request.observacion()));
            return respuesta(propuesta);
        });
    }
    private void validarPago(PagoEntity pago,ProponerRequest request,UUID actor) {
        if (pago.getEstado()!=PagoEntity.Estado.REVISION || pago.getUsuarioId().equals(actor)
                || request.versionPago()==null || pago.getVersion()!=request.versionPago()
                || !pago.getUpc().equals(request.upc()) || request.monto()==null || pago.getMonto().compareTo(request.monto())!=0)
            throw error(HttpStatus.CONFLICT);
        if (request.resultado()==null || request.folioProveedor()==null || request.folioProveedor().isBlank()
                || request.evidenciaProveedor()==null || request.evidenciaProveedor().isBlank()
                || request.motivo()==null || request.motivo().isBlank()) throw error(HttpStatus.BAD_REQUEST);
        if (request.resultado()!=Resultado.NO_APLICADO_DEFINITIVO) {
            if (!positivo(request.idTransaccion()) || !positivo(request.autorizacion())) throw error(HttpStatus.BAD_REQUEST);
        } else if (request.idTransaccion()!=null || request.autorizacion()!=null) throw error(HttpStatus.BAD_REQUEST);
    }
    private void aplicar(PagoEntity pago,ProponerRequest detalle) {
        boolean debitar=detalle.resultado()==Resultado.APLICADO;
        if (debitar && pagos.existsByIdTransaccionAndIdNot(detalle.idTransaccion(),pago.getId())) throw error(HttpStatus.CONFLICT);
        if (detalle.resultado()==Resultado.DUPLICADO_YA_CONTABILIZADO) {
            var anterior=pagos.findByIdTransaccion(detalle.idTransaccion()).orElseThrow(() -> error(HttpStatus.CONFLICT));
            if (anterior.getId().equals(pago.getId()) || anterior.getEstado()!=PagoEntity.Estado.APLICADO
                    || !anterior.getUsuarioId().equals(pago.getUsuarioId()) || pago.getHuellaOperacion()==null
                    || !pago.getHuellaOperacion().equals(anterior.getHuellaOperacion())
                    || pago.getMonto().compareTo(anterior.getMonto())!=0 || !detalle.autorizacion().equals(anterior.getAutorizacion()))
                throw error(HttpStatus.CONFLICT);
        }
        var cuenta=cuentas.findByNumeroCuentaForUpdate(pago.getNumeroCuenta());
        if (cuenta==null || !cuenta.getId().equals(pago.getCuentaId()) || cuenta.getSaldoRetenido().compareTo(pago.getMonto())<0)
            throw error(HttpStatus.CONFLICT);
        cuenta.resolverRetencion(pago.getMonto(),debitar,Instant.now()); cuentas.saveAndFlush(cuenta);
        pago.setEstado(debitar?PagoEntity.Estado.APLICADO:PagoEntity.Estado.RECHAZADO);
        pago.setIntentoConfirmacion(null); pago.setProximaConfirmacion(null); pago.setMotivoRevision(null);
        if (debitar) { pago.setIdTransaccion(detalle.idTransaccion()); pago.setAutorizacion(detalle.autorizacion()); }
        pagos.saveAndFlush(pago);
    }
    private ProponerRequest detalle(ConciliacionPagoEntity propuesta) {
        try { return json.readValue(crypto.revelar(propuesta.getContenidoCifrado()),ProponerRequest.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw error(HttpStatus.SERVICE_UNAVAILABLE); }
    }
    private Propuesta respuesta(ConciliacionPagoEntity propuesta) {
        var detalle=detalle(propuesta);
        return new Propuesta(propuesta.getId(),propuesta.getPagoId(),propuesta.getEstado().name(),detalle.resultado(),
                propuesta.getPropuestoPor(),propuesta.getDecididoPor(),propuesta.getCreadoEn(),propuesta.getVenceEn(),detalle);
    }
    private boolean positivo(String valor) { return valor!=null && valor.matches("[0-9]{1,60}") && new BigDecimal(valor).signum()>0; }
    private String serializar(Object valor) {
        try { return json.writeValueAsString(valor); } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw error(HttpStatus.SERVICE_UNAVAILABLE); }
    }
    private void evidencia(UUID pago,String metodo,Object valor) { evidencias.saveAndFlush(new PagoEvidenciaEntity(pago,metodo,crypto.proteger(serializar(valor)))); }
    private PagoException error(HttpStatus status) { return new PagoException(status,"No fue posible completar la conciliacion"); }
}
