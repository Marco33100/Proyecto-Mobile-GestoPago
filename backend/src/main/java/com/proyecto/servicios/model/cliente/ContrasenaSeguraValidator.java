package com.proyecto.servicios.model.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {
    public static final int MINIMO = 8;
    public static final int MAXIMO = 12;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return esValida(value);
    }

    public static boolean esValida(String value) {
        return value != null && value.length() >= MINIMO && value.length() <= MAXIMO
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
