package com.proyecto.servicios.model.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import com.proyecto.servicios.support.ClienteDatos;

public class MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate> {

    @Override
    public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext context) {
        return fechaNacimiento == null || ClienteDatos.esMayorDeEdad(fechaNacimiento, LocalDate.now());
    }
}
