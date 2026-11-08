package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.PagoProperties;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.repositorys.gestopago.ProductRepository;
import com.proyecto.servicios.service.PagoGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Short, atomic ledger transactions. No provider/network call is allowed in this class. */
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class PagoLedger {
    private static final String CAMPO_ID_PRODUCTO = "idProducto";
    private static final String CAMPO_ID_SERVICIO = "idServicio";
    private static final String CAMPO_REFERENCIA = "referencia";
    private static final String PAGO_NO_DISPONIBLE = "Pago no disponible";
    public record Envio(UUID pagoId, String json) {
        @Override public String toString() { return "Envio[protegido]"; }
    }
    public record Confirmacion(UUID pagoId, String json, UUID intento) {
        @Override public String toString() { return "Confirmacion[protegida]"; }
    }
    public record Preparacion(PagoResponse pago, boolean requiereReferencia, String json) {
        @Override public String toString() { return "Preparacion[protegida]"; }
    }
    private final PagoRepository pagos;
    private final PagoEvidenciaRepository evidencias;
    private final CuentaRepository cuentas;
    private final UserRepository usuarios;
    private final SegundoFactorRepository claves;
    private final ProductRepository productos;
    private final PagoProperties properties;
    private final PagoCrypto crypto;
    private final TotpAuthorization totp;
    private record DecisionAutorizacion(Envio envio, boolean denegada) { }
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public PagoLedger(PagoRepository pagos, PagoEvidenciaRepository evidencias, CuentaRepository cuentas, UserRepository usuarios,
                      SegundoFactorRepository claves, ProductRepository productos, PagoProperties properties,
                      PagoCrypto crypto, ObjectMapper json, TotpAuthorization totp,
                      @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        this.pagos = pagos; this.evidencias = evidencias; this.cuentas = cuentas; this.usuarios = usuarios; this.claves = claves;
        this.totp = totp; this.productos = productos; this.properties = properties; this.crypto = crypto; this.json = json;
        tx = new TransactionTemplate(manager);
    }
    public Preparacion preparar(PrepararRequest request, UUID actor) {
        String huella = PagoCrypto.huella(serializar(Arrays.asList(request.numeroCuenta().trim(), request.idServicio(),
                request.idProducto(), request.tipoFront(), request.monto().setScale(2).toPlainString(),
                texto(request.telefono()), texto(request.referencia()))));
        return tx.execute(status -> {
            // Same lock order for preparation, authorization and enrollment; serializes a user's intentions across accounts.
            var user = usuarioActivo(actor);
            var existente = pagos.bloquear(request.solicitudId()).orElse(null);
            if (existente != null) {
                return preparacionExistente(existente, actor, huella);
            }
            var clave = claves.findById(actor).orElseThrow(() -> error(HttpStatus.CONFLICT, "Activa el segundo factor antes de pagar"));
            if (clave.getSecretoCifrado() == null) throw error(HttpStatus.CONFLICT, "Activa el segundo factor antes de pagar");
            var cuenta = cuentas.findByNumeroCuentaForUpdate(request.numeroCuenta().trim());
            validarCuenta(cuenta, user, request.monto());
            // Serialize two concurrent preparations of the same account before inserting the idempotency key.
            existente = pagos.findById(request.solicitudId()).orElse(null);
            if (existente != null) {
                return preparacionExistente(existente, actor, huella);
            }
            var producto = productos.consultarParaPago(request.idProducto()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Producto no disponible en el catalogo local"));
            validarProductoPreparacion(producto, request);
            int tipo = request.tipoFront();
            BigDecimal monto = request.monto().setScale(2);
            validarLimites(producto, monto);
            validarPresupuesto(actor, monto);
            String telefono = telefonoPreparacion(producto, request);
            String referencia = referenciaPreparacion(request);
            Instant ahora = Instant.now();
            String huellaOperacion = huellaOperacion(request.idServicio(), request.idProducto(), tipo, monto,
                    tipo == 1 ? telefono : null, tipo == 1 ? null : referencia);
            validarDuplicado(actor, request.solicitudId(), huellaOperacion, ahora);
            var pago = new PagoEntity();
            pago.setId(request.solicitudId()); pago.setUsuarioId(actor); pago.setCuentaId(cuenta.getId());
            pago.setNumeroCuenta(cuenta.getNumeroCuenta()); pago.setHuella(huella); pago.setHuellaClave(clave.getHuella());
            pago.setHuellaOperacion(huellaOperacion); pago.setHuellaCatalogo(huellaCatalogo(producto));
            pago.setUpc(PagoCrypto.upc()); pago.setConcepto(producto.getProductName()); pago.setMonto(monto);
            pago.setTipoFront(tipo);
            String reglas = snapshotReglas(producto);
            pago.setReglasCifradas(crypto.proteger(reglas)); pago.setHuellaReglas(PagoCrypto.huella(reglas));
            pago.setEstado(PagoEntity.Estado.PREPARADO); pago.setCreadoEn(ahora);
            pago.setVenceEn(ahora.plus(properties.getAutorizacionTtl())); pago.setReferenciaValidada(tipo != 4);
            pago.setRequierePin(Objects.equals(producto.getCategoryServiceTypeId(), 10) || Objects.equals(producto.getCategoryServiceTypeId(), 11));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(CAMPO_ID_PRODUCTO, request.idProducto()); payload.put(CAMPO_ID_SERVICIO, request.idServicio());
            payload.put("telefono", telefono);
            payload.put("horaLocal", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of(properties.getZonaHoraria())).format(ahora));
            if (tipo != 1) { payload.put(CAMPO_REFERENCIA, referencia); payload.put("montoPago", monto); }
            payload.put("upc", pago.getUpc()); payload.put("unidad", properties.getUnidad());
            String contenido = serializar(payload);
            pago.setPayloadCifrado(crypto.proteger(contenido));
            // Columna historica de V17: conservarla para registros anteriores; ya no se emiten retos ECDSA.
            pago.setReto("TOTP-v1");
            pagos.saveAndFlush(pago);
            return preparacion(pago);
        });
    }
    private Preparacion preparacionExistente(PagoEntity pago, UUID actor, String huella) {
        propio(pago, actor);
        if (!huella.equals(pago.getHuella())) throw error(HttpStatus.CONFLICT, "La solicitud ya identifica otro pago");
        return preparacion(pago);
    }
    private void validarProductoPreparacion(com.proyecto.servicios.entity.gestopago.ProductEntity producto, PrepararRequest request) {
        if (!Objects.equals(producto.getServiceId(), request.idServicio()) || !Objects.equals(producto.getFrontType(), request.tipoFront()))
            throw error(HttpStatus.BAD_REQUEST, "El servicio y tipoFront deben corresponder al producto");
        int tipo = request.tipoFront();
        if (tipo != 1 && tipo != 2 && tipo != 4) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Este tipo de producto requiere un contrato adicional del proveedor");
        if (tipo == 1 && (producto.getPrice() == null || producto.getPrice().compareTo(request.monto().setScale(2)) != 0))
            throw error(HttpStatus.BAD_REQUEST, "El monto debe coincidir con el precio del producto");
    }
    private String telefonoPreparacion(com.proyecto.servicios.entity.gestopago.ProductEntity producto, PrepararRequest request) {
        if (request.tipoFront() != 1) return "1111111111";
        if (Objects.equals(producto.getCategoryServiceTypeId(), 10) || Objects.equals(producto.getCategoryServiceTypeId(), 11)) return "#1111111111";
        String telefono = texto(request.telefono());
        if (!telefono.matches("\\d{10,20}")) throw error(HttpStatus.BAD_REQUEST, "El telefono no tiene el formato requerido");
        return telefono;
    }
    private String referenciaPreparacion(PrepararRequest request) {
        String referencia = texto(request.referencia());
        if (request.tipoFront() != 1 && (referencia.isEmpty() || referencia.chars().anyMatch(Character::isISOControl)))
            throw error(HttpStatus.BAD_REQUEST, "La referencia es obligatoria y no admite caracteres de control");
        return referencia;
    }
    private Preparacion preparacion(PagoEntity pago) {
        return new Preparacion(respuesta(pago), pago.getEstado() == PagoEntity.Estado.PREPARADO && !pago.isReferenciaValidada(),
                crypto.revelar(pago.getPayloadCifrado()));
    }
    public PagoResponse referenciaValidada(UUID id, UUID actor, PagoGateway.Referencia referencia) {
        return tx.execute(status -> {
            var pago = bloquearPropio(id, actor);
            if (pago.getEstado() == PagoEntity.Estado.PREPARADO && !pago.isReferenciaValidada()) {
                actualizarReferencia(pago, referencia);
                pagos.saveAndFlush(pago);
            }
            return respuesta(pago);
        });
    }
    private void actualizarReferencia(PagoEntity pago, PagoGateway.Referencia referencia) {
        if (!Instant.now().isBefore(pago.getVenceEn())) {
            pago.setEstado(PagoEntity.Estado.EXPIRADO);
            return;
        }
        if (referencia == null || referencia.estado() == PagoGateway.EstadoReferencia.INCIERTA
                || (referencia.estado() == PagoGateway.EstadoReferencia.VALIDA && referencia.saldo() == null))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible verificar la referencia");
        boolean valida = referencia.estado() == PagoGateway.EstadoReferencia.VALIDA;
        pago.setReferenciaValidada(valida);
        if (!valida) pago.setEstado(PagoEntity.Estado.RECHAZADO);
        pago.setConsultaReferenciaEn(Instant.now());
        pago.setConsultaReferenciaCifrada(crypto.proteger(serializar(referencia)));
    }
    public Envio autorizar(AutorizarRequest request, UUID actor) {
        var decision = tx.execute(status -> autorizarPago(request, actor));
        if (decision.denegada()) throw error(HttpStatus.FORBIDDEN, "Codigo incorrecto, vencido o ya utilizado");
        return decision.envio();
    }
    private DecisionAutorizacion autorizarPago(AutorizarRequest request, UUID actor) {
        var user = usuarioActivo(actor);
        var pago = bloquearPropio(request.pagoId(), actor);
        if (pago.getEstado() != PagoEntity.Estado.PREPARADO) return new DecisionAutorizacion(null, false);
        if (!Instant.now().isBefore(pago.getVenceEn())) {
            pago.setEstado(PagoEntity.Estado.EXPIRADO); pagos.saveAndFlush(pago); return new DecisionAutorizacion(null, false);
        }
        var clave = claves.findById(actor).orElseThrow(() -> error(HttpStatus.FORBIDDEN, "Segundo factor no disponible"));
        if (!pago.getHuellaClave().equals(clave.getHuella()) || clave.getSecretoCifrado() == null)
            throw error(HttpStatus.FORBIDDEN, "El segundo factor cambio; prepara nuevamente el pago");
        if (!pago.isReferenciaValidada()) throw error(HttpStatus.CONFLICT, "La referencia aun no ha sido validada");
        var payload = leerPayload(pago);
        var producto = productos.consultarParaPago(payload.path(CAMPO_ID_PRODUCTO).asInt()).orElse(null);
        // No cambiar el importe ni el destino que el cliente reviso al preparar.
        if (!catalogoVigente(pago, producto)) {
            pago.setEstado(PagoEntity.Estado.EXPIRADO); pagos.saveAndFlush(pago); return new DecisionAutorizacion(null, false);
        }
        validarLimites(producto, pago.getMonto());
        validarPresupuesto(actor, pago.getMonto());
        if (Objects.equals(producto.getFrontType(), 4))
            validarImporteReferencia(pago, payload);
        validarDuplicado(actor, pago.getId(), pago.getHuellaOperacion(), Instant.now());
        var cuenta = cuentas.findByNumeroCuentaForUpdate(pago.getNumeroCuenta());
        validarCuenta(cuenta, user, pago.getMonto());
        if (!totp.consume(clave, request.codigo())) return new DecisionAutorizacion(null, true);
        cuenta.retener(pago.getMonto(), Instant.now()); cuentas.saveAndFlush(cuenta);
        pago.setEstado(PagoEntity.Estado.ENVIADO); pago.setEnviadoEn(Instant.now());
        pago.setProximaConfirmacion(pago.getEnviadoEn().plusSeconds(75));
        pagos.saveAndFlush(pago);
        return new DecisionAutorizacion(new Envio(pago.getId(), crypto.revelar(pago.getPayloadCifrado())), false);
    }
    private boolean catalogoVigente(PagoEntity pago, com.proyecto.servicios.entity.gestopago.ProductEntity producto) {
        return producto != null && pago.getHuellaCatalogo() != null && pago.getHuellaOperacion() != null
                && pago.getHuellaCatalogo().equals(huellaCatalogo(producto)) && pago.getHuellaReglas() != null
                && pago.getHuellaReglas().equals(PagoCrypto.huella(snapshotReglas(producto)));
    }
    public PagoResponse registrarResultadoEnvio(UUID id, PagoGateway.Respuesta resultado) {
        return resolver(id, resultado, false, null);
    }
    public PagoResponse resolverConfirmacion(Confirmacion confirmacion, PagoGateway.Respuesta resultado) {
        Objects.requireNonNull(confirmacion.intento(), "Se requiere un intento de confirmacion");
        return resolver(confirmacion.pagoId(), resultado, true, confirmacion.intento());
    }
    private PagoResponse resolver(UUID id, PagoGateway.Respuesta resultado, boolean confirmacion, UUID intento) {
        return tx.execute(status -> {
            var pago = pagos.bloquear(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, PAGO_NO_DISPONIBLE));
            boolean descartada = !abierto(pago) || (confirmacion && !intento.equals(pago.getIntentoConfirmacion()));
            archivarRespuesta(pago, resultado, confirmacion, intento, descartada);
            if (descartada) return respuesta(pago);
            if (confirmacion) pago.setIntentoConfirmacion(null);
            actualizarRecibo(pago, resultado);
            pago.setCodigoProveedor(resultado.codigo());
            procesarResultado(pago, resultado, confirmacion);
            pagos.saveAndFlush(pago);
            return respuesta(pago);
        });
    }
    private void archivarRespuesta(PagoEntity pago, PagoGateway.Respuesta resultado, boolean confirmacion,
                                   UUID intento, boolean descartada) {
        String cuerpo = resultado.evidencia() == null || resultado.evidencia().isEmpty()
                ? "Sin respuesta verificable" : resultado.evidencia();
        String registro = (descartada ? "Respuesta tardia/obsoleta archivada sin modificar saldo ni estado.\n" : "")
                + (confirmacion ? "Intento de confirmacion: " + intento + "\n" : "") + cuerpo;
        evidencias.save(new PagoEvidenciaEntity(pago.getId(), confirmacion ? "confirmTx" : "sendTx", crypto.proteger(registro)));
    }
    private void actualizarRecibo(PagoEntity pago, PagoGateway.Respuesta resultado) {
        if (resultado.evidencia() == null || resultado.evidencia().isEmpty()) return;
        String cifrado = crypto.proteger(resultado.evidencia());
        if (pago.getReciboCifrado() == null || resultado.pin() != null) pago.setReciboCifrado(cifrado);
    }
    private boolean identificadorPositivo(String valor) {
        return valor != null && valor.matches("\\d{1,60}") && new BigDecimal(valor).signum() > 0;
    }
    private void procesarResultado(PagoEntity pago, PagoGateway.Respuesta resultado, boolean confirmacion) {
        boolean aplicado = resultado.estado() == PagoGateway.Estado.APLICADO;
        boolean identificadores = identificadorPositivo(resultado.idTransaccion()) && identificadorPositivo(resultado.autorizacion());
        boolean importeIncoherente = resultado.monto() != null && pago.getMonto().compareTo(resultado.monto()) != 0;
        registrarDuplicadoProveedor(pago, resultado, confirmacion, identificadores);
        if (pago.isDuplicadoProveedor() && "06".equals(resultado.codigo())) {
            resolverDuplicado(pago, resultado, confirmacion, identificadores && !importeIncoherente);
        } else if (aplicado && respuestaInconsistente(pago, resultado, confirmacion, identificadores, importeIncoherente)) {
            revisar(pago, "RESPUESTA_INCONSISTENTE");
        } else if (confirmacion && "70".equals(resultado.codigo()) && pago.getEstado() == PagoEntity.Estado.ENVIADO) {
            // Un sendTx pausado aun podria continuar; la ausencia en el proveedor no libera la retencion.
            revisar(pago, "ENVIO_SIN_CIERRE");
        } else if (aplicado || rechazoDefinitivo(resultado, confirmacion)) {
            liquidarRetencion(pago, aplicado, resultado);
        } else {
            pendiente(pago);
        }
    }
    private void registrarDuplicadoProveedor(PagoEntity pago, PagoGateway.Respuesta resultado,
                                            boolean confirmacion, boolean identificadores) {
        if (confirmacion || !"06".equals(resultado.codigo())) return;
        pago.setDuplicadoProveedor(true);
        if (identificadores) {
            pago.setIdTransaccionPrevia(resultado.idTransaccion());
            pago.setAutorizacionPrevia(resultado.autorizacion());
        }
    }
    private void resolverDuplicado(PagoEntity pago, PagoGateway.Respuesta resultado,
                                   boolean confirmacion, boolean datosCoherentes) {
        if (datosCoherentes && coincideConAplicadoAnterior(pago, resultado)) {
            // El debito ya esta contabilizado localmente; no cobrar dos veces.
            liquidarRetencion(pago, false, resultado);
        } else if (confirmacion) {
            revisar(pago, "DUPLICADO_NO_CONCILIADO");
        } else {
            pendiente(pago);
        }
    }
    private boolean respuestaInconsistente(PagoEntity pago, PagoGateway.Respuesta resultado, boolean confirmacion,
                                           boolean identificadores, boolean importeIncoherente) {
        boolean codigoAplicado = confirmacion ? "06".equals(resultado.codigo()) : "01".equals(resultado.codigo());
        return pago.isDuplicadoProveedor() || !identificadores || !codigoAplicado || importeIncoherente
                || (!confirmacion && resultado.monto() == null)
                || pagos.existsByIdTransaccionAndIdNot(resultado.idTransaccion(), pago.getId())
                || (pago.getIdTransaccion() != null && !pago.getIdTransaccion().equals(resultado.idTransaccion()));
    }
    private boolean rechazoDefinitivo(PagoGateway.Respuesta resultado, boolean confirmacion) {
        return resultado.estado() == PagoGateway.Estado.RECHAZADO
                && (confirmacion ? "70".equals(resultado.codigo()) : "NOT_SENT".equals(resultado.codigo()));
    }
    private void pendiente(PagoEntity pago) {
        pago.setEstado(PagoEntity.Estado.PENDIENTE);
        programarConfirmacion(pago);
    }
    public PagoResponse consultar(UUID id, UUID actor) {
        return tx.execute(status -> {
            var pago = pagos.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, PAGO_NO_DISPONIBLE));
            propio(pago, actor); return respuesta(pago);
        });
    }
    public PagoResponse cancelar(UUID id, UUID actor) {
        return tx.execute(status -> {
            var pago = bloquearPropio(id, actor);
            if (pago.getEstado() == PagoEntity.Estado.PREPARADO) {
                pago.setEstado(PagoEntity.Estado.EXPIRADO); pagos.saveAndFlush(pago);
            }
            return respuesta(pago);
        });
    }
    public List<UUID> pendientes() {
        return tx.execute(status -> pagos.pendientes(List.of(PagoEntity.Estado.ENVIADO, PagoEntity.Estado.PENDIENTE), Instant.now(), PageRequest.of(0, 10)));
    }
    public Confirmacion reclamarConfirmacion(UUID id) {
        return tx.execute(status -> {
            var pago = pagos.bloquear(id).orElse(null);
            Instant ahora = Instant.now();
            if (pago == null || !abierto(pago) || ahora.isBefore(pago.getProximaConfirmacion()) || ahora.isBefore(pago.getEnviadoEn().plusSeconds(62))) return null;
            if (!ahora.isBefore(pago.getEnviadoEn().plus(Duration.ofHours(24)))) {
                revisar(pago, "VENTANA_CONFIRMACION_VENCIDA"); pagos.saveAndFlush(pago); return null;
            }
            // Persist a bounded lease before the network call; another instance cannot immediately claim it.
            pago.setIntentoConfirmacion(UUID.randomUUID());
            if (pago.getNumeroConfirmaciones() < Integer.MAX_VALUE) pago.setNumeroConfirmaciones(pago.getNumeroConfirmaciones() + 1);
            pago.setProximaConfirmacion(ahora.plusSeconds(120)); pagos.saveAndFlush(pago);
            return new Confirmacion(pago.getId(), crypto.revelar(pago.getPayloadCifrado()), pago.getIntentoConfirmacion());
        });
    }
    private PagoResponse respuesta(PagoEntity pago) {
        String pin = null;
        String leyenda = null;
        if (pago.getEstado() == PagoEntity.Estado.APLICADO && pago.getReciboCifrado() != null) {
            var recibo = GestopagoPagoGateway.interpretar(crypto.revelar(pago.getReciboCifrado()), false);
            pin = recibo.pin(); leyenda = recibo.leyenda();
        }
        String aviso = avisoEstado(pago, pin);
        var payload = leerPayload(pago);
        BigDecimal deuda = saldoReferencia(pago);
        var regla = reglaReferencia(payload);
        boolean esTipo4 = esReferencia(pago, payload);
        boolean reglasVigentes = reglasVigentes(pago, payload);
        boolean disponible = disponibleParaAutorizar(pago, payload, deuda, regla, esTipo4, reglasVigentes);
        aviso = avisoPreparacion(pago, deuda, esTipo4, disponible, reglasVigentes, aviso);
        String telefono = payload.has(CAMPO_REFERENCIA) || pago.isRequierePin() ? null : payload.path("telefono").asText();
        String referencia = payload.has(CAMPO_REFERENCIA) ? payload.path(CAMPO_REFERENCIA).asText() : null;
        return new PagoResponse(pago.getId(), pago.getEstado().name(), pago.getUpc(), pago.getNumeroCuenta(), pago.getConcepto(),
                pago.getMonto(), BigDecimal.ZERO,
                pago.getVenceEn(), pago.getIdTransaccion(), pago.getAutorizacion(), pin, leyenda, aviso, telefono, referencia,
                deuda, pago.getConsultaReferenciaEn(), modalidadReferencia(esTipo4, regla), disponible);
    }
    private String avisoEstado(PagoEntity pago, String pin) {
        String aviso = switch (pago.getEstado()) {
            case ENVIADO, PENDIENTE -> "Pago pendiente de confirmacion. No repitas la operacion; el importe sigue retenido.";
            case REVISION -> "Pago en conciliacion. No repitas la operacion; el importe sigue retenido.";
            case PREPARADO -> "Revisa los datos y autoriza con tu codigo de autenticacion. Aun no se ha cobrado.";
            case EXPIRADO -> "El pago preparado vencio, fue cancelado o sus condiciones cambiaron. Prepara una nueva solicitud; no se realizo un cobro.";
            case RECHAZADO -> "No se realizo un nuevo cobro. La retencion, si existia, fue liberada.";
            case APLICADO -> "Conserva tu comprobante.";
        };
        if (pago.getEstado() == PagoEntity.Estado.APLICADO && pago.isRequierePin() && (pin == null || pin.isBlank()))
            aviso = "Pago aplicado; el PIN esta pendiente de recuperacion con el proveedor. Conserva la autorizacion y no repitas el pago.";
        if (pago.isDuplicadoProveedor() && pago.getEstado() == PagoEntity.Estado.RECHAZADO)
            aviso = "El proveedor reporto una operacion anterior; no se realizo un nuevo cargo y la retencion fue liberada.";
        return aviso;
    }
    private boolean esReferencia(PagoEntity pago, com.fasterxml.jackson.databind.JsonNode payload) {
        return Objects.equals(pago.getTipoFront(), 4) || pago.getConsultaReferenciaCifrada() != null
                || (pago.getTipoFront() == null && payload.has(CAMPO_REFERENCIA));
    }
    private boolean reglasVigentes(PagoEntity pago, com.fasterxml.jackson.databind.JsonNode payload) {
        return pago.getHuellaReglas() != null && pago.getHuellaReglas().equals(PagoCrypto.huella(
                snapshotReglas(payload.path(CAMPO_ID_PRODUCTO).asInt(), payload.path(CAMPO_ID_SERVICIO).asInt(), pago.getTipoFront())));
    }
    private boolean disponibleParaAutorizar(PagoEntity pago, com.fasterxml.jackson.databind.JsonNode payload,
            BigDecimal deuda, PagoProperties.ReglaReferencia regla, boolean esTipo4, boolean reglasVigentes) {
        var configuradas = properties.getReglasReferenciaPorProducto();
        boolean reglaInvalida = regla == null && configuradas != null && configuradas.containsKey(payload.path(CAMPO_ID_PRODUCTO).asInt());
        return pago.getEstado() == PagoEntity.Estado.PREPARADO && Instant.now().isBefore(pago.getVenceEn())
                && reglasVigentes && pago.isReferenciaValidada()
                && (!esTipo4 || (!reglaInvalida && importeReferenciaPermitido(pago.getMonto(), deuda, regla)));
    }
    private String avisoPreparacion(PagoEntity pago, BigDecimal deuda, boolean esTipo4, boolean disponible,
                                     boolean reglasVigentes, String aviso) {
        if (pago.getEstado() != PagoEntity.Estado.PREPARADO) return aviso;
        if (!reglasVigentes) return "Las condiciones de pago cambiaron; cancela y prepara una nueva solicitud. Aun no se ha cobrado.";
        if (!esTipo4) return aviso;
        if (deuda == null) return "No hay una consulta de deuda verificable. Aun no se ha cobrado.";
        if (deuda.signum() <= 0) return "La referencia no presenta deuda pendiente. No se habilita el cobro.";
        if (!disponible) return "El importe solicitado no cumple las condiciones de pago. Cancela y prepara nuevamente; aun no se ha cobrado.";
        return aviso;
    }
    private String modalidadReferencia(boolean esTipo4, PagoProperties.ReglaReferencia regla) {
        if (!esTipo4) return null;
        return regla == null ? "HASTA_SALDO_SIN_POLITICA" : regla.modalidad().name();
    }
    private BigDecimal saldoReferencia(PagoEntity pago) {
        if (pago.getConsultaReferenciaCifrada() == null) return null;
        try {
            return json.readValue(crypto.revelar(pago.getConsultaReferenciaCifrada()), PagoGateway.Referencia.class).saldo();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Registro de referencia no verificable"); }
    }
    private PagoProperties.ReglaReferencia reglaReferencia(com.fasterxml.jackson.databind.JsonNode payload) {
        var reglas = properties.getReglasReferenciaPorProducto();
        var regla = reglas == null ? null : reglas.get(payload.path(CAMPO_ID_PRODUCTO).asInt());
        return regla != null && regla.modalidad() != null
                && Objects.equals(regla.idServicio(), payload.path(CAMPO_ID_SERVICIO).asInt()) ? regla : null;
    }
    private boolean importeReferenciaPermitido(BigDecimal monto, BigDecimal deuda, PagoProperties.ReglaReferencia regla) {
        if (deuda == null || deuda.signum() <= 0) return false;
        if (regla == null) return monto.signum() > 0 && monto.compareTo(deuda) <= 0;
        return switch (regla.modalidad()) {
            case IMPORTE_EXACTO -> monto.compareTo(deuda) == 0;
            case PARCIAL_HASTA_SALDO -> monto.signum() > 0 && monto.compareTo(deuda) <= 0;
        };
    }
    private void validarImporteReferencia(PagoEntity pago, com.fasterxml.jackson.databind.JsonNode payload) {
        var regla = reglaReferencia(payload);
        var configuradas = properties.getReglasReferenciaPorProducto();
        if (regla == null && configuradas != null && configuradas.containsKey(payload.path(CAMPO_ID_PRODUCTO).asInt()))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Regla de referencia invalida");
        if (!importeReferenciaPermitido(pago.getMonto(), saldoReferencia(pago), regla))
            throw error(HttpStatus.CONFLICT, "El importe no corresponde a la deuda y regla confirmadas");
    }
    private boolean abierto(PagoEntity pago) { return pago.getEstado() == PagoEntity.Estado.ENVIADO || pago.getEstado() == PagoEntity.Estado.PENDIENTE; }
    private PagoEntity bloquearPropio(UUID id, UUID actor) {
        var pago = pagos.bloquear(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, PAGO_NO_DISPONIBLE)); propio(pago, actor); return pago;
    }
    private void propio(PagoEntity pago, UUID actor) {
        if (!pago.getUsuarioId().equals(actor)) throw error(HttpStatus.NOT_FOUND, PAGO_NO_DISPONIBLE);
    }
    private UserEntity usuarioActivo(UUID actor) {
        var user = usuarios.bloquearUsuarioDeSesion(actor).orElseThrow(() -> error(HttpStatus.FORBIDDEN, "Usuario no disponible"));
        if (!user.isEnabled() || user.getCliente() == null || !user.getCliente().isActivo()) throw error(HttpStatus.FORBIDDEN, "Usuario no disponible");
        return user;
    }
    private void validarLimites(com.proyecto.servicios.entity.gestopago.ProductEntity producto, BigDecimal monto) {
        var limites = properties.getLimitesPorProducto();
        var limite = limites == null ? null : limites.get(producto.getProductId());
        if (limite == null) return;
        if (!Objects.equals(limite.idServicio(), producto.getServiceId())
                || !importeConfigurable(limite.minimo()) || !importeConfigurable(limite.maximo())
                || limite.minimo().compareTo(limite.maximo()) > 0)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "El producto no tiene limites de monto confirmados");
        if (monto.compareTo(limite.minimo()) < 0 || monto.compareTo(limite.maximo()) > 0)
            throw error(HttpStatus.BAD_REQUEST, "El monto esta fuera del rango permitido para el producto");
    }
    private boolean importeConfigurable(BigDecimal valor) {
        return valor != null && valor.signum() > 0 && valor.stripTrailingZeros().scale() <= 2
                && valor.compareTo(new BigDecimal("99999999999999999.99")) <= 0;
    }
    private void validarPresupuesto(UUID actor, BigDecimal monto) {
        BigDecimal operacion = properties.getMaximoPorOperacion();
        BigDecimal diario = properties.getMaximoDiarioPorCliente();
        if ((operacion != null && !importeConfigurable(operacion)) || (diario != null && !importeConfigurable(diario)))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Limites del negocio no verificables");
        if (operacion != null && monto.compareTo(operacion) > 0)
            throw error(HttpStatus.BAD_REQUEST, "El importe supera el limite por operacion");
        if (diario != null) {
            // usuarioActivo mantiene el bloqueo del usuario: dos cuentas comparten el mismo presupuesto.
            var inicio = LocalDate.now(ZoneId.of(properties.getZonaHoraria())).atStartOfDay(ZoneId.of(properties.getZonaHoraria()));
            var usado = pagos.importeEnviadoEnPeriodo(actor, inicio.toInstant(), inicio.plusDays(1).toInstant(),
                    List.of(PagoEntity.Estado.ENVIADO, PagoEntity.Estado.PENDIENTE, PagoEntity.Estado.REVISION, PagoEntity.Estado.APLICADO))
                    .orElse(BigDecimal.ZERO);
            if (usado.add(monto).compareTo(diario) > 0)
                throw error(HttpStatus.CONFLICT, "Se alcanzo el limite diario del cliente");
        }
    }
    private String snapshotReglas(com.proyecto.servicios.entity.gestopago.ProductEntity producto) {
        return snapshotReglas(producto.getProductId(),producto.getServiceId(),producto.getFrontType());
    }
    private String snapshotReglas(Integer producto,Integer servicio,Integer tipo) {
        var limite = properties.getLimitesPorProducto() == null ? null : properties.getLimitesPorProducto().get(producto);
        var regla = properties.getReglasReferenciaPorProducto() == null ? null : properties.getReglasReferenciaPorProducto().get(producto);
        return serializar(Arrays.asList("v1", producto, servicio, tipo,
                limite == null ? null : limite.idServicio(), limite == null ? null : importeCanonico(limite.minimo()),
                limite == null ? null : importeCanonico(limite.maximo()),
                Objects.equals(tipo, 4) && regla != null ? regla.idServicio() : null,
                Objects.equals(tipo, 4) && regla != null ? regla.modalidad() : null,
                importeCanonico(properties.getMaximoPorOperacion()), importeCanonico(properties.getMaximoDiarioPorCliente()),
                properties.getZonaHoraria()));
    }
    private String importeCanonico(BigDecimal importe) { return importe == null ? null : importe.stripTrailingZeros().toPlainString(); }
    private String huellaCatalogo(com.proyecto.servicios.entity.gestopago.ProductEntity producto) {
        return PagoCrypto.huella(serializar(Arrays.asList(producto.getProductId(), producto.getServiceId(),
                producto.getFrontType(), producto.getCategoryServiceTypeId(), producto.getProductName(),
                producto.getPrice() == null ? null : producto.getPrice().stripTrailingZeros().toPlainString(),
                producto.getHasCheckDigit(), producto.getReferenceType())));
    }
    private String huellaOperacion(Integer servicio, Integer producto, int tipo, BigDecimal monto, String telefono, String referencia) {
        return PagoCrypto.huella(serializar(Arrays.asList(servicio, producto, tipo, monto.setScale(2).toPlainString(), telefono, referencia)));
    }
    private void validarDuplicado(UUID actor, UUID id, String huella, Instant ahora) {
        if (pagos.existeEquivalenteActivo(actor, id, huella, ahora, PagoEntity.Estado.PREPARADO,
                List.of(PagoEntity.Estado.ENVIADO, PagoEntity.Estado.PENDIENTE, PagoEntity.Estado.REVISION)))
            throw error(HttpStatus.CONFLICT, "Ya existe una operacion equivalente sin resolver; consulta su estado");
    }
    private com.fasterxml.jackson.databind.JsonNode leerPayload(PagoEntity pago) {
        try { return json.readTree(crypto.revelar(pago.getPayloadCifrado())); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Registro de pago no verificable"); }
    }
    private boolean coincideConAplicadoAnterior(PagoEntity pago, PagoGateway.Respuesta resultado) {
        if (pago.getIdTransaccionPrevia() == null || pago.getAutorizacionPrevia() == null
                || !pago.getIdTransaccionPrevia().equals(resultado.idTransaccion())
                || !pago.getAutorizacionPrevia().equals(resultado.autorizacion()) || pago.getHuellaOperacion() == null) return false;
        var anterior = pagos.findByIdTransaccion(resultado.idTransaccion()).orElse(null);
        return anterior != null && !anterior.getId().equals(pago.getId()) && anterior.getEstado() == PagoEntity.Estado.APLICADO
                && anterior.getUsuarioId().equals(pago.getUsuarioId()) && pago.getHuellaOperacion().equals(anterior.getHuellaOperacion())
                && pago.getMonto().compareTo(anterior.getMonto()) == 0 && resultado.autorizacion().equals(anterior.getAutorizacion());
    }
    private void liquidarRetencion(PagoEntity pago, boolean aplicar, PagoGateway.Respuesta resultado) {
        var cuenta = cuentas.findByNumeroCuentaForUpdate(pago.getNumeroCuenta());
        cuenta.resolverRetencion(pago.getMonto(), aplicar, Instant.now()); cuentas.saveAndFlush(cuenta);
        pago.setEstado(aplicar ? PagoEntity.Estado.APLICADO : PagoEntity.Estado.RECHAZADO);
        pago.setIntentoConfirmacion(null); pago.setProximaConfirmacion(null); pago.setMotivoRevision(null);
        if (aplicar) { pago.setIdTransaccion(resultado.idTransaccion()); pago.setAutorizacion(resultado.autorizacion()); }
    }
    private void revisar(PagoEntity pago, String motivo) {
        pago.setEstado(PagoEntity.Estado.REVISION); pago.setMotivoRevision(motivo);
        pago.setIntentoConfirmacion(null); pago.setProximaConfirmacion(null);
    }
    private void programarConfirmacion(PagoEntity pago) {
        Instant ahora = Instant.now();
        Instant limite = pago.getEnviadoEn().plus(Duration.ofHours(24));
        if (!ahora.isBefore(limite)) { revisar(pago, "VENTANA_CONFIRMACION_VENCIDA"); return; }
        // Do not shorten an in-flight worker's persisted lease when a late send response arrives.
        if (pago.getIntentoConfirmacion() != null && pago.getProximaConfirmacion() != null
                && ahora.isBefore(pago.getProximaConfirmacion())) return;
        long base = Math.max(60, Math.min(900, properties.getConfirmacionIntervalo().getSeconds()));
        long espera = Math.min(900, base * (1L << Math.min(4, Math.max(0, pago.getNumeroConfirmaciones() - 1))));
        Instant siguiente = ahora.plusSeconds(espera);
        pago.setProximaConfirmacion(siguiente.isBefore(limite) ? siguiente : limite);
    }
    private void validarCuenta(CuentaEntity cuenta, UserEntity user, BigDecimal monto) {
        if (cuenta == null || !cuenta.getCliente().getId().equals(user.getCliente().getId())) throw error(HttpStatus.NOT_FOUND, "Cuenta no disponible");
        if (!cuenta.isActiva()) throw error(HttpStatus.CONFLICT, "La cuenta esta inactiva");
        if (monto == null || monto.signum() <= 0 || cuenta.getSaldoDisponible().compareTo(monto) < 0) throw error(HttpStatus.CONFLICT, "Saldo disponible insuficiente");
    }
    private String texto(String valor) { return valor == null ? "" : valor.trim(); }
    private String serializar(Object valor) {
        try { return json.writeValueAsString(valor); } catch (Exception e) { throw new IllegalStateException("No fue posible preparar el pago"); }
    }
    private PagoException error(HttpStatus status, String mensaje) { return new PagoException(status, mensaje); }
}
