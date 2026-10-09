package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActualizarDomicilioRequest(
        @NotBlank @Size(max = 100) String calle,
        @NotBlank @Size(max = 20) String numeroExterior,
        @Size(max = 20) String numeroInterior,
        @NotBlank @Size(max = 100) String colonia,
        @NotBlank @Size(max = 100) String municipio,
        @NotBlank @Size(max = 100) String estado,
        @NotBlank @Pattern(regexp = "^[0-9]{5}$", message = "debe contener exactamente 5 digitos")
        String codigoPostal,
        @NotBlank @Size(max = 60) String pais
) {
}
