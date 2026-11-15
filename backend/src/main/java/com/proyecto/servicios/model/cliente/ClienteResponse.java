package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClienteResponse(
        Long id,
        String nombre,
        String segundoNombre,
        String apellidoPaterno,
        String apellidoMaterno,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate fechaNacimiento,
        String curp,
        String rfc,
        Sexo sexo,
        String nacionalidad,
        EstadoCivil estadoCivil,
        String referenciaReconocimientoFacial,
        String correo,
        String telefonoMovil,
        String telefonoAlternativo,
        String ocupacion,
        String empresa,
        BigDecimal ingresoMensual,
        boolean activo,
        DomicilioResponse domicilio,
        List<CuentaResponse> cuentas,
        Instant fechaCreacion,
        Instant fechaActualizacion
) {
    public ClienteResponse {
        cuentas = cuentas == null ? List.of() : List.copyOf(cuentas);
    }
}
