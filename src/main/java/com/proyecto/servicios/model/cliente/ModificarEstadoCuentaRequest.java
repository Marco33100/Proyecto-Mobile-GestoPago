package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import static com.proyecto.servicios.support.CuentaDatos.*;

/** El numero selecciona la cuenta, no es una propiedad modificable del parche. */
public record ModificarEstadoCuentaRequest(
        @NotBlank(message = "El numero de cuenta es obligatorio")
        @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta,
        @NotNull(message = "Los cambios de estado son obligatorios") @Valid ActualizarEstadoCuentaRequest cambios
) implements SolicitudJson { }
