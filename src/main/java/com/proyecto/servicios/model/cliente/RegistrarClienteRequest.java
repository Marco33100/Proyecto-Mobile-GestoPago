package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarClienteRequest(
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$") String nombre,
        @Size(max = 50)
        @Pattern(regexp = "^ *$|^ *[\\p{L}]+(?: +[\\p{L}]+)* *$") String segundoNombre,
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$") String apellidoPaterno,
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$") String apellidoMaterno,
        @NotNull @Past @MayorDeEdad
        @JsonDeserialize(using = FechaSinHoraDeserializer.class)
        @Schema(type = "string", format = "date", example = "1990-01-01")
        LocalDate fechaNacimiento,
        @NotBlank @Pattern(
                regexp = "(?i)^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$",
                message = "la CURP debe tener un formato válido de 18 caracteres"
        ) String curp,
        @NotBlank @Pattern(
                regexp = "(?i)^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$",
                message = "el RFC debe tener un formato válido de 12 o 13 caracteres"
        ) String rfc,
        @NotNull Sexo sexo,
        @NotBlank @Size(max = 60) String nacionalidad,
        @NotNull EstadoCivil estadoCivil,
        @Size(max = 255) String referenciaReconocimientoFacial,
        @NotBlank @Email @Size(max = 100) String correo,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "debe contener exactamente 10 dígitos")
        String telefonoMovil,
        @Pattern(regexp = "^\\s*$|^[0-9]{10}$", message = "debe estar vacío o contener exactamente 10 dígitos")
        String telefonoAlternativo,
        @NotBlank @Size(max = 100) String ocupacion,
        @NotBlank @Size(max = 150) String empresa,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal ingresoMensual,
        @NotNull @Valid ActualizarDomicilioRequest domicilio,
        @NotBlank(message = "La contrasena es obligatoria")
        @ContrasenaSegura
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Contrasena para el acceso del cliente; nunca se devuelve en respuestas")
        String contrasena
) {
    @Override
    public String toString() {
        return "RegistrarClienteRequest[datos personales y contrasena protegidos]";
    }
}
