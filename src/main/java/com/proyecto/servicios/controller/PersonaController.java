package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.service.PersonaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class PersonaController {


    @Autowired
    private PersonaService personaService;


    @PostMapping(value = "/personas",produces =MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> crearUser(@Valid @RequestBody PersonasRequest personasRequest){

        return new ResponseEntity<>(personaService.creaPersona(personasRequest), HttpStatus.OK);
    }

    @PutMapping(value = "/personasActualiza", produces =MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> actualizUser(@Valid @RequestBody PersonasRequest personasRequest){

        return new ResponseEntity<>(personaService.actualizaPersona(personasRequest), HttpStatus.OK);
    }
    @PutMapping(value = "/personasElimina", produces =MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse> actualizUser(@Valid @RequestBody EliminaPersonaRequest personasRequest){

        return new ResponseEntity<>(personaService.eliminaPersona(personasRequest), HttpStatus.OK);
    }
}
