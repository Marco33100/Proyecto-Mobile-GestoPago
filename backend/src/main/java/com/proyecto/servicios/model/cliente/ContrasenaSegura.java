package com.proyecto.servicios.model.cliente;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = ContrasenaSeguraValidator.class)
public @interface ContrasenaSegura {
    String message() default "La contrasena debe tener entre 8 y 72 caracteres, maximo 72 bytes UTF-8, mayuscula, minuscula, numero y caracter especial";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
