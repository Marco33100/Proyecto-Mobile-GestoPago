package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Lista cerrada de campos modificables: no admite saldo, titular ni identificadores.
 * Solo existe un campo modificable: omitir activa deja un parche sin cambios y se rechaza con 400.
 * activa=null tambien es invalido: el estado de una cuenta no admite null ni una operacion de borrado.
 * Las demas propiedades se conservan; no se interpretan valores por defecto como actualizaciones.
 */
@Schema(description = "Parche de estado: activa es el unico campo modificable. {} y activa=null se rechazan con 400.")
public record ActualizarEstadoCuentaRequest(
        @NotNull(message = "El campo activa es obligatorio")
        @JsonDeserialize(using = BooleanEstrictoDeserializer.class)
        @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Boolean JSON obligatorio y no nulo. Solo se permite false; true devuelve 409.")
        Boolean activa
) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String nombre, Object valor) {
        throw new IllegalArgumentException("La solicitud contiene campos no permitidos");
    }
}
