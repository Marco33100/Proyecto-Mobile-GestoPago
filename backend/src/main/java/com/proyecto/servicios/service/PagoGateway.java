package com.proyecto.servicios.service;

import java.math.BigDecimal;

public interface PagoGateway {
    enum Estado { APLICADO, RECHAZADO, INCIERTO }
    enum EstadoReferencia { VALIDA, INVALIDA, INCIERTA }
    record Referencia(EstadoReferencia estado, String codigo, BigDecimal saldo, String evidencia) {
        public static Referencia incierta() { return new Referencia(EstadoReferencia.INCIERTA, null, null, ""); }
        @Override public String toString() { return "Referencia[protegida]"; }
    }
    record Respuesta(Estado estado, String codigo, String idTransaccion, String autorizacion,
                     BigDecimal monto, BigDecimal comision, String pin, String leyenda, String evidencia) {
        public static Respuesta incierta() { return new Respuesta(Estado.INCIERTO, "UNKNOWN", null, null, null, null, null, null, ""); }
        public static Respuesta noEnviada() { return new Respuesta(Estado.RECHAZADO, "NOT_SENT", null, null, null, null, null, null, ""); }
        @Override public String toString() { return "RespuestaProveedor[protegida]"; }
    }
    Respuesta enviar(String json);
    Respuesta confirmar(String json);
    Referencia validarReferencia(Integer producto, Integer servicio, String referencia);
}
