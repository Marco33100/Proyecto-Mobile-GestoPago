package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.model.SolicitudJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Selector compuesto de consulta; no sustituye el UUID interno ni la identidad de la sesion. */
public record UsuarioConsultaRequest(
        @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios.")
        @Size(max = 203)
        @Schema(description = "Nombre completo del usuario; coincidencia exacta sin distinguir mayusculas",
                example = "Marco Morales Martinez")
        String nombre,
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254)
        String correo
) implements SolicitudJson { }
