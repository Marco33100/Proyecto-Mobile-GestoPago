package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.model.SolicitudJson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos que el propio cliente puede cambiar. Identidad (nombre, fecha de nacimiento, CURP, RFC,
 * sexo, nacionalidad) y correo de acceso solo los cambia un ejecutivo.
 */
public record ActualizarMisDatosRequest(
        @NotBlank(message = "El teléfono es obligatorio.")
        @Pattern(regexp = "^\\d{10}$", message = "El teléfono debe contener exactamente 10 dígitos.")
        String telefonoMovil,
        @Pattern(regexp = "^\\s*$|^\\d{10}$", message = "debe estar vacio o contener exactamente 10 digitos")
        String telefonoAlternativo,
        @NotNull EstadoCivil estadoCivil,
        @NotBlank @Size(max = 100) String ocupacion,
        @Size(max = 150) String empresa,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal ingresoMensual,
        @NotNull @Valid ActualizarDomicilioRequest domicilio
) implements SolicitudJson {
}
