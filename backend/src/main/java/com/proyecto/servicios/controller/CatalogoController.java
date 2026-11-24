package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.CatalogosRegistroResponse;
import com.proyecto.servicios.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/catalogos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Catalogos", description = "Opciones de los campos de lista del registro de clientes")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class CatalogoController {

    private final CatalogoService catalogoService;

    @Autowired
    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/registro")
    @Operation(summary = "Paises, nacionalidades y ocupaciones para el registro",
            description = "Publico: lo usa el registro propio antes de iniciar sesion.")
    public ResponseEntity<GenericResponse<CatalogosRegistroResponse>> registro() {
        return ResponseEntity.ok(GenericResponse.exito("Catalogos consultados correctamente",
                catalogoService.catalogosRegistro()));
    }
}
