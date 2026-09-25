package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.service.PersonaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(
        name = "app.database.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class InMemoryPersonaServiceImpl implements PersonaService {

    private final Map<String, PersonasRequest> personas = new ConcurrentHashMap<>();

    @Override
    public PersonaResponse crearPersona(PersonasRequest request) {
        PersonaResponse response = new PersonaResponse();
        if (request == null || isBlank(request.getNombre())) {
            response.setCodigo(1);
            response.setMensaje("El nombre es obligatorio");
            return response;
        }

        personas.put(request.getNombre(), copyOf(request));
        response.setCodigo(0);
        response.setMensaje("La persona ha sido creada correctamente");
        response.setNombre(request.getNombre());
        response.setApellidoP(request.getApellidoP());
        response.setApellidoMaterno(request.getApellidoMaterno());
        return response;
    }

    @Override
    public GenericResponse eliminarPersona(EliminaPersonaRequest request) {
        GenericResponse response = new GenericResponse();
        if (request == null || isBlank(request.getNombre()) || personas.remove(request.getNombre()) == null) {
            response.setCodigo(1);
            response.setMensaje("La persona no existe");
            return response;
        }

        response.setCodigo(0);
        response.setMensaje("La persona ha sido eliminada correctamente");
        return response;
    }

    @Override
    public GenericResponse actualizarPersona(PersonasRequest request) {
        GenericResponse response = new GenericResponse();
        if (request == null || isBlank(request.getNombre()) || !personas.containsKey(request.getNombre())) {
            response.setCodigo(1);
            response.setMensaje("La persona no existe");
            return response;
        }

        personas.put(request.getNombre(), copyOf(request));
        response.setCodigo(0);
        response.setMensaje("La persona ha sido actualizada correctamente");
        return response;
    }

    private PersonasRequest copyOf(PersonasRequest source) {
        PersonasRequest copy = new PersonasRequest();
        copy.setNombre(source.getNombre());
        copy.setApellidoP(source.getApellidoP());
        copy.setApellidoMaterno(source.getApellidoMaterno());
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
