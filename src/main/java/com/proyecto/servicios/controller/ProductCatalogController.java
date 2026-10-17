package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.GenericResponse;
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
@RequestMapping(value = "/api/products", produces = "application/json")
@Tag(name = "Catálogo para clientes", description = "Endpoint cliente: Redis, PostgreSQL y fallback a Gestopago")
@SecurityRequirement(name = "bearerAuth")
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    public ProductCatalogController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    @Operation(summary = "Consulta el catálogo para el cliente con caché multinivel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo recuperado correctamente"),
            @ApiResponse(
                    responseCode = "500",
                    description = "No fue posible procesar el catalogo de productos",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "El proveedor de productos no esta disponible",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class))
            )
    })
    public ResponseEntity<GenericResponse<ProductListResponse>> obtenerProductos() {
        var resultado = productCatalogService.obtenerProductos();
        boolean hayProductos = resultado.getProductos() != null && resultado.getProductos().getProductos() != null
                && !resultado.getProductos().getProductos().isEmpty();
        return ResponseEntity.ok(GenericResponse.consulta("Catalogo consultado correctamente", resultado, hayProductos));
    }
}
