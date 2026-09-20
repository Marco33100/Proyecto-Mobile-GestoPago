package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.IntegrationErrorResponse;
import com.proyecto.servicios.service.ProductCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Catálogo de productos", description = "Consulta Cache-Aside: Redis, PostgreSQL y Gestopago")
@SecurityRequirement(name = "bearerAuth")
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    public ProductCatalogController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    @Operation(summary = "Consulta el catálogo de productos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo recuperado correctamente"),
            @ApiResponse(
                    responseCode = "500",
                    description = "Código 2: error de persistencia, deserialización o procesamiento local",
                    content = @Content(schema = @Schema(implementation = IntegrationErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Código 1: Gestopago no disponible, conexión rechazada o timeout",
                    content = @Content(schema = @Schema(implementation = IntegrationErrorResponse.class))
            )
    })
    public ResponseEntity<ProductListResponse> obtenerProductos() {
        return ResponseEntity.ok(productCatalogService.obtenerProductos());
    }
}
