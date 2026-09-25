package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.service.PersonaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @PostMapping(
            value = "/personas",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GenericResponse> crearUsuario(@Valid @RequestBody PersonasRequest request) {
        return ResponseEntity.ok(personaService.crearPersona(request));
    }

    @PutMapping(
            value = "/personasActualiza",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GenericResponse> actualizarUsuario(@Valid @RequestBody PersonasRequest request) {
        return ResponseEntity.ok(personaService.actualizarPersona(request));
    }

    @PutMapping(
            value = "/personasElimina",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GenericResponse> eliminarUsuario(@Valid @RequestBody EliminaPersonaRequest request) {
        return ResponseEntity.ok(personaService.eliminarPersona(request));
    }
}
