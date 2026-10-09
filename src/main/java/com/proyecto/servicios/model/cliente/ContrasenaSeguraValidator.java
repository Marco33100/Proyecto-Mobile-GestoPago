package com.proyecto.servicios.model.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return esValida(value);
    }

    public static boolean esValida(String value) {
        return value != null && value.length() >= 8 && value.length() <= 72
                && value.getBytes(StandardCharsets.UTF_8).length <= 72
                && value.codePoints().anyMatch(Character::isUpperCase)
                && value.codePoints().anyMatch(Character::isLowerCase)
                && value.codePoints().anyMatch(Character::isDigit)
                && value.codePoints().anyMatch(ContrasenaSeguraValidator::esEspecial)
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean esEspecial(int caracter) {
        return !Character.isLetterOrDigit(caracter) && !Character.isWhitespace(caracter)
                && !Character.isSpaceChar(caracter) && !Character.isISOControl(caracter)
                && Character.getType(caracter) != Character.FORMAT;
    }
}
