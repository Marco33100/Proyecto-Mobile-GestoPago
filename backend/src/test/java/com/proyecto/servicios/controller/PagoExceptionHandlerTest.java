package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.PagoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PagoExceptionHandlerTest {
    private final PagoExceptionHandler handler = new PagoExceptionHandler();

    private String mensaje(HttpStatus status, String interno) {
        var respuesta = handler.negocio(new PagoException(status, interno));
        assertThat(respuesta.getStatusCode()).isEqualTo(status);
        assertThat(respuesta.getHeaders().getCacheControl()).isEqualTo("no-store");
        return respuesta.getBody().mensaje();
    }

    @Test
    void motivosDeNegocioLleganAlClienteConUnMensajeClaro() {
        assertThat(mensaje(HttpStatus.FORBIDDEN, "Codigo incorrecto, vencido o ya utilizado"))
                .contains("codigo de autenticacion no es correcto");
        assertThat(mensaje(HttpStatus.CONFLICT, "Se alcanzo el limite diario del cliente"))
                .contains("limite diario");
        assertThat(mensaje(HttpStatus.BAD_REQUEST, "El telefono no tiene el formato requerido"))
                .contains("entre 10 y 20 digitos");
        assertThat(mensaje(HttpStatus.CONFLICT, "Saldo disponible insuficiente"))
                .isEqualTo("Saldo disponible insuficiente para realizar el pago.");
    }

    @Test
    void mensajesInternosNoSeExponen() {
        assertThat(mensaje(HttpStatus.BAD_REQUEST, "El servicio y tipoFront deben corresponder al producto"))
                .isEqualTo("Los datos enviados no son validos. Revisa la solicitud.");
        assertThat(mensaje(HttpStatus.FORBIDDEN, "Usuario no disponible"))
                .isEqualTo("No fue posible autorizar la operacion. Verifica tus permisos y datos de autenticacion.");
        assertThat(mensaje(HttpStatus.SERVICE_UNAVAILABLE, "Regla de referencia invalida"))
                .isEqualTo("No es posible procesar la solicitud en este momento. Intentalo mas tarde.");
        assertThat(mensaje(HttpStatus.CONFLICT, null))
                .startsWith("No se puede completar la operacion");
    }
}
