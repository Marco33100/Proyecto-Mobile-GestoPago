package com.proyecto.servicios.model.cliente;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MayorDeEdadValidator.class)
public @interface MayorDeEdad {
    String message() default "el cliente debe tener al menos 18 años";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
