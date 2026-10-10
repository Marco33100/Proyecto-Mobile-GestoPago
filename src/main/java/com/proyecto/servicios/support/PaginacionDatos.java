package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;

/** Limites comunes de las consultas paginadas de clientes y cuentas. */
public final class PaginacionDatos {
    public static final int PAGINA_MINIMA = 0;
    public static final int TAMANIO_MINIMO = 1;
    public static final int TAMANIO_MAXIMO = 100;

    private PaginacionDatos() {
    }

    public static void validar(int pagina, int tamanio) {
        if (pagina < PAGINA_MINIMA || tamanio < TAMANIO_MINIMO || tamanio > TAMANIO_MAXIMO) {
            throw new ClienteValidationException("Pagina o tamanio fuera del rango permitido");
        }
    }
}
