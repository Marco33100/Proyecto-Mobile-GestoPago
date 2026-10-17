package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

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

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActualizarClienteRequest(
        @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios.") @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$", message = "solo puede contener letras y espacios")
        String nombre,
        @Size(max = 50)
        @Pattern(regexp = "^ *$|^ *[\\p{L}]+(?: +[\\p{L}]+)* *$", message = "solo puede contener letras y espacios")
        String segundoNombre,
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$", message = "solo puede contener letras y espacios")
        String apellidoPaterno,
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^ *[\\p{L}]+(?: +[\\p{L}]+)* *$", message = "solo puede contener letras y espacios")
        String apellidoMaterno,
        @NotNull @Past @MayorDeEdad
        @JsonDeserialize(using = FechaSinHoraDeserializer.class)
        LocalDate fechaNacimiento,
        @NotNull Sexo sexo,
        @NotBlank @Size(max = 60) String nacionalidad,
        @NotNull EstadoCivil estadoCivil,
        @Size(max = 255) String referenciaReconocimientoFacial,
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 100) String correo,
        @NotBlank(message = "El teléfono es obligatorio.")
        @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe contener exactamente 10 dígitos.")
        String telefonoMovil,
        @Pattern(regexp = "^\\s*$|^[0-9]{10}$", message = "debe estar vacio o contener exactamente 10 digitos")
        String telefonoAlternativo,
        @NotBlank @Size(max = 100) String ocupacion,
        @NotBlank @Size(max = 150) String empresa,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal ingresoMensual,
        @NotNull @Valid ActualizarDomicilioRequest domicilio
) implements SolicitudJson {
}
