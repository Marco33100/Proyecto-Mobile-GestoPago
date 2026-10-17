package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.GenericResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorCamposTest {
    @Test
    void devuelveSoloCodigoYMensajeSinValoresRechazados() throws Exception {
        var errors = new BeanPropertyBindingResult(new Object(), "cliente");
        errors.addError(new FieldError("cliente", "correo", "correo-privado", false, null, null,
                "El correo electrónico no tiene un formato válido."));
        errors.addError(new FieldError("cliente", "nombre", "nombre-privado", false, null, null,
                "El nombre es obligatorio y no puede contener solo espacios."));
        GenericResponse<Void> response = validar(errors);
        var json = new ObjectMapper().valueToTree(response);
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.path("codigo").asInt()).isEqualTo(2);
        assertThat(json.path("mensaje").asText()).isEqualTo(
                "Datos no validos: {correo=El correo electrónico no tiene un formato válido., nombre=El nombre es obligatorio y no puede contener solo espacios.}");
        assertThat(json.toString()).doesNotContain("correo-privado", "nombre-privado", "fieldErrors", "timestamp");
    }

    @Test
    void variosErroresDelMismoCampoConservanElPrimerMensaje() throws Exception {
        var errors = new BeanPropertyBindingResult(new Object(), "cliente");
        errors.addError(new FieldError("cliente", "correo", "El correo electrónico es obligatorio."));
        errors.addError(new FieldError("cliente", "correo", "El correo electrónico no tiene un formato válido."));
        assertThat(validar(errors).getMensaje())
                .isEqualTo("Datos no validos: {correo=El correo electrónico es obligatorio.}");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void mensajeObligatorioTienePrioridadSinDependerDelOrdenDeLasRestricciones(boolean obligatorioPrimero) throws Exception {
        var errors = new BeanPropertyBindingResult(new Object(), "cliente");
        var obligatorio = new FieldError("cliente", "nombre", "   ", false,
                new String[]{"NotBlank"}, null, "El nombre es obligatorio y no puede contener solo espacios.");
        var formato = new FieldError("cliente", "nombre", "   ", false,
                new String[]{"Pattern"}, null, "solo puede contener letras y espacios");
        errors.addError(obligatorioPrimero ? obligatorio : formato);
        errors.addError(obligatorioPrimero ? formato : obligatorio);

        assertThat(validar(errors).getMensaje()).isEqualTo(
                "Datos no validos: {nombre=El nombre es obligatorio y no puede contener solo espacios.}");
    }

    @Test
    void contemplaRestriccionesDelDtoCompletoYMensajesAusentes() throws Exception {
        var errors = new BeanPropertyBindingResult(new Object(), "cliente");
        errors.addError(new ObjectError("cliente", "Los campos del DTO no son compatibles"));
        errors.addError(new FieldError("cliente", "nombre", null));
        assertThat(validar(errors).getMensaje())
                .contains("dto=Los campos del DTO no son compatibles", "nombre=El campo no es valido");
    }

    private GenericResponse<Void> validar(BeanPropertyBindingResult errors) throws Exception {
        var method = getClass().getDeclaredMethod("dto", Object.class);
        var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), errors);
        var response = new ValidadorCampos().dtoValidation(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        return response.getBody();
    }

    private void dto(Object body) { }
}
