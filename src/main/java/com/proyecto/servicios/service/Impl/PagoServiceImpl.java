package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.PagoProperties;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.pago.PagoDtos.*;
import com.proyecto.servicios.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service @PreAuthorize("hasRole('CLIENTE')")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class PagoServiceImpl implements PagoService {
    private final PagoLedger ledger;
    private final PagoGateway gateway;
    private final PagoProperties properties;
    private final PagoCrypto crypto;
    private final ObjectMapper json;
    private final LoginAttemptLimiter limiter;
    public PagoServiceImpl(PagoLedger ledger, PagoGateway gateway, PagoProperties properties,
                           PagoCrypto crypto, ObjectMapper json, LoginAttemptLimiter limiter) {
        this.ledger = ledger; this.gateway = gateway; this.properties = properties; this.crypto = crypto;
        this.json = json; this.limiter = limiter;
    }
    private void habilitado() {
        if (!properties.isEnabled()) throw new PagoException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no Disponible");
        if (!properties.getUnidad().matches("[A-Za-z0-9 _.-]{3,25}") || properties.getAutorizacionTtl().isNegative()
                || properties.getAutorizacionTtl().isZero() || properties.getAutorizacionTtl().compareTo(java.time.Duration.ofMinutes(15)) > 0
                || properties.getConfirmacionIntervalo().compareTo(java.time.Duration.ofSeconds(60)) < 0)
            throw new PagoException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no Disponible");
        crypto.validarConfiguracion();
    }
    @Override public PagoResponse preparar(PrepararRequest request) {
        habilitado(); var actor = PagoIdentidad.actual();
        var resultado = ledger.preparar(request, actor.userId());
        if (!resultado.requiereReferencia()) return resultado.pago();
        try {
            var payload = json.readTree(resultado.json());
            var referencia = gateway.validarReferencia(payload.path("idProducto").asInt(), payload.path("idServicio").asInt(), payload.path("referencia").asText());
            if (referencia == null || referencia.estado() == PagoGateway.EstadoReferencia.INCIERTA)
                throw new PagoException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no Disponible");
            var verificado = ledger.referenciaValidada(resultado.pago().pagoId(), actor.userId(), referencia);
            if (referencia.estado() == PagoGateway.EstadoReferencia.INVALIDA)
                throw new PagoException(HttpStatus.BAD_REQUEST, "Referencia no valida");
            return verificado;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Registro de pago no verificable"); }
    }
    @Override public PagoResponse autorizar(AutorizarRequest request) {
        habilitado(); var actor = PagoIdentidad.actual();
        limiter.checkAccount("pago:" + actor.userId());
        var envio = ledger.autorizar(request, actor.userId());
        limiter.loginSucceeded("pago:" + actor.userId());
        if (envio == null) return ledger.consultar(request.pagoId(), actor.userId());
        PagoGateway.Respuesta resultado;
        try { resultado = gateway.enviar(envio.json()); }
        catch (Exception e) { resultado = PagoGateway.Respuesta.incierta(); }
        return ledger.registrarResultadoEnvio(envio.pagoId(), resultado);
    }
    @Override public PagoResponse consultar(ConsultaRequest request) {
        habilitado(); return ledger.consultar(request.pagoId(), PagoIdentidad.actual().userId());
    }
    @Override public PagoResponse cancelar(ConsultaRequest request) {
        habilitado(); return ledger.cancelar(request.pagoId(), PagoIdentidad.actual().userId());
    }
}
