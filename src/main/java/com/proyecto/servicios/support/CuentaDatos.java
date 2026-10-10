package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Formato del identificador publico generado por el backend, no de una CLABE. */
public final class CuentaDatos {
    public static final String NUMERO_CUENTA_REGEX = "(?i)^CTA-[0-9A-F]{20}$";
    public static final String NUMERO_CUENTA_MENSAJE = "El numero de cuenta debe tener el formato CTA- y 20 caracteres hexadecimales";
    private static final Pattern NUMERO_CUENTA = Pattern.compile(NUMERO_CUENTA_REGEX);

    private CuentaDatos() { }

    public static String normalizarNumeroCuenta(String valor) {
        if (valor == null || !NUMERO_CUENTA.matcher(valor.trim()).matches()) {
            throw new ClienteValidationException(NUMERO_CUENTA_MENSAJE);
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
