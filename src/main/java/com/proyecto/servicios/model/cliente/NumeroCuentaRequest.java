package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import static com.proyecto.servicios.support.CuentaDatos.*;

public record NumeroCuentaRequest(
        @NotBlank(message = "El numero de cuenta es obligatorio")
        @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta
) implements SolicitudJson { }
