package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.CatalogosRegistroResponse;

public interface CatalogoService {

    CatalogosRegistroResponse catalogosRegistro();

    /** Lanza ClienteValidationException si algun valor no esta en su catalogo; nacionalidad null no se revisa. */
    void validarSeleccion(String nacionalidad, String pais, String ocupacion);
}
