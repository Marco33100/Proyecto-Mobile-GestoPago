package com.proyecto.servicios.service;

import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;

public interface PersonaService {
    PersonaResponse creaPersona(PersonasRequest personasRequest);
    GenericResponse eliminaPersona(EliminaPersonaRequest eliminaPersonaRequest);
    GenericResponse actualizaPersona(PersonasRequest personasRequest);
}
