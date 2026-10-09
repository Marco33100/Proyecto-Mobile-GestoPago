package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reglas compartidas de registro, actualizacion y validacion de clientes. */
public final class ClienteDatos {
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final int EDAD_MINIMA = 18;

    private ClienteDatos() {
    }

    public static String normalizarTexto(String valor) {
        return ESPACIOS.matcher(valor.trim()).replaceAll(" ");
    }

    public static String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : normalizarTexto(valor);
    }

    public static String normalizarMayusculas(String valor) {
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizarCorreo(String valor) {
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean esMayorDeEdad(LocalDate fechaNacimiento, LocalDate hoy) {
        return fechaNacimiento != null && !fechaNacimiento.isAfter(hoy.minusYears(EDAD_MINIMA));
    }

    public static void validarMayoriaDeEdad(LocalDate fechaNacimiento) {
        if (!esMayorDeEdad(fechaNacimiento, LocalDate.now())) {
            throw new ClienteValidationException("El cliente debe tener al menos 18 anos");
        }
    }

    public static void validarNombres(String nombre, String apellidoPaterno, String apellidoMaterno) {
        if (nombreInvalido(nombre) || nombreInvalido(apellidoPaterno) || nombreInvalido(apellidoMaterno)) {
            throw new ClienteValidationException("Nombre y apellidos deben tener al menos 2 caracteres");
        }
    }

    private static boolean nombreInvalido(String valor) {
        return valor == null || normalizarTexto(valor).length() < 2;
    }
}
