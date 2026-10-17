package com.proyecto.servicios.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Codigo de resultado independiente del estado HTTP: 0 sin resultados, 1 exito, 2 error. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {
    public static final int SIN_RESULTADOS = 0;
    public static final int EXITO = 1;
    public static final int ERROR = 2;

    @Schema(description = "0: consulta sin resultados; 1: exito con datos; 2: error", allowableValues = {"0", "1", "2"})
    private Integer codigo;
    private String mensaje;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T datos;

    public static GenericResponse<Void> error(String mensaje) {
        return new GenericResponse<>(ERROR, mensaje, null);
    }

    public static <T> GenericResponse<T> exito(String mensaje, T datos) {
        return new GenericResponse<>(EXITO, mensaje, datos);
    }

    public static <T> GenericResponse<T> consulta(String mensaje, T datos, boolean hayResultados) {
        return new GenericResponse<>(hayResultados ? EXITO : SIN_RESULTADOS,
                hayResultados ? mensaje : "No se encontraron datos", hayResultados ? datos : null);
    }
}
