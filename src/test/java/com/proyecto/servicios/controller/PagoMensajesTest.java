package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.pago.PagoDtos.PagoResponse;
import com.proyecto.servicios.model.pago.PagoDtos.Resultado;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagoMensajesTest {
    private final PagoExceptionHandler handler = new PagoExceptionHandler();

    @Test void saldoInsuficienteDevuelveMensajeUtilSinDatos() {
        var respuesta = handler.negocio(new PagoException(HttpStatus.CONFLICT, "Saldo disponible insuficiente"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(respuesta.getBody().codigo()).isEqualTo("02");
        assertThat(respuesta.getBody().mensaje()).contains("Saldo disponible insuficiente");
        assertThat(respuesta.getBody().datos()).isNull();
    }

    @Test void errorDesconocidoNoExponeDetalleInterno() {
        var respuesta = handler.negocio(new PagoException(HttpStatus.CONFLICT, "SQL password secreto"));
        assertThat(respuesta.getBody().mensaje()).doesNotContain("SQL", "password", "secreto");
        assertThat(handler.inesperado(new RuntimeException("clave secreta")).getBody().mensaje())
                .doesNotContain("clave", "secreta");
    }

    @Test void pagoInciertoIndicaQueNoDebeRepetirse() {
        var pago = mock(PagoResponse.class);
        when(pago.estado()).thenReturn("PENDIENTE", "REVISION");
        var pendiente = Resultado.de(pago);
        var revision = Resultado.de(pago);
        assertThat(pendiente.codigo()).isEqualTo("02");
        assertThat(pendiente.mensaje()).contains("Consulta su estado");
        assertThat(revision.mensaje()).contains("no lo vuelvas a enviar");
    }

    @Test void pagoAplicadoConservaCodigoYMensajeDeExito() {
        var pago = mock(PagoResponse.class);
        when(pago.estado()).thenReturn("APLICADO");
        assertThat(Resultado.de(pago).codigo()).isEqualTo("01");
        assertThat(Resultado.de(pago).mensaje()).isEqualTo("Pago realizado correctamente");
    }
}
