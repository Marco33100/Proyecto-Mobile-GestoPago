package com.proyecto.servicios.model.cliente;

import java.util.List;

/** Opciones de los campos de lista del registro, ordenadas alfabeticamente. */
public record CatalogosRegistroResponse(
        List<String> paises,
        List<String> nacionalidades,
        List<String> ocupaciones
) {
}
