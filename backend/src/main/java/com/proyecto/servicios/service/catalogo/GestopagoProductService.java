package com.proyecto.servicios.service.catalogo;

import com.proyecto.servicios.model.product.ProductListResponse;

/** Caso de uso que consulta directamente Gestopago y actualiza los almacenamientos locales. */
public interface GestopagoProductService {

    ProductListResponse obtenerYGuardarProductos();
}
