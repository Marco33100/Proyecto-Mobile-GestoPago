package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import java.time.LocalDate;
import java.time.ZoneId;
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
        if (!esMayorDeEdad(fechaNacimiento, LocalDate.now(ZoneId.systemDefault()))) {
            throw new ClienteValidationException("El cliente debe tener al menos 18 anos");
        }
    }

    public static void validarNombres(String nombre, String apellidoPaterno, String apellidoMaterno) {
        if (nombreInvalido(nombre) || nombreInvalido(apellidoPaterno) || nombreInvalido(apellidoMaterno)) {
            throw new ClienteValidationException("Nombre y apellidos deben tener al menos 3 letras");
        }
    }

    /** Opcional, pero si se captura debe tener al menos 3 letras. */
    public static void validarSegundoNombre(String valor) {
        String texto = normalizarOpcional(valor);
        if (texto != null && texto.length() < 3) {
            throw new ClienteValidationException("El segundo nombre debe tener al menos 3 letras");
        }
    }

    /** Ocupaciones del catalogo que pueden no tener ingreso. */
    public static final java.util.Set<String> OCUPACIONES_SIN_INGRESO =
            java.util.Set.of("Desempleado(a)", "Estudiante", "Ama(o) de casa",
                    "Jubilado(a) o pensionado(a)");

    /** El ingreso es obligatorio si hay empresa o la ocupacion implica un ingreso. */
    public static void validarIngreso(String ocupacion, String empresa, java.math.BigDecimal ingreso) {
        boolean puedeOmitirse = normalizarOpcional(empresa) == null && ocupacion != null
                && OCUPACIONES_SIN_INGRESO.contains(normalizarTexto(ocupacion));
        if (ingreso == null && !puedeOmitirse) {
            throw new ClienteValidationException("El ingreso mensual es obligatorio si tienes empresa o un empleo");
        }
    }

    private static boolean nombreInvalido(String valor) {
        return valor == null || normalizarTexto(valor).length() < 3;
    }
}
