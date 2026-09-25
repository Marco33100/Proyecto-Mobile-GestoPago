package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.product.ProductListResponse;

final class ProductCatalogResponses {

    private ProductCatalogResponses() {
    }

    static boolean hasProducts(ProductListResponse response) {
        return response != null
                && response.getProductos() != null
                && response.getProductos().getProductos() != null
                && !response.getProductos().getProductos().isEmpty();
    }
}
