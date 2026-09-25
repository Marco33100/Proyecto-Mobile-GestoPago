package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.service.PersonaService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class PersonasServiceImpl implements PersonaService {

    private final PersonasRepository personasRepository;

    @Autowired
    public PersonasServiceImpl(PersonasRepository personasRepository) {
        this.personasRepository = personasRepository;
    }

    @Override
    public PersonaResponse crearPersona(PersonasRequest request) {
        Personas persona = new Personas();
        persona.setNombre(request.getNombre());
        persona.setApellidoMaterno(request.getApellidoMaterno());
        persona.setApellidoP(request.getApellidoP());
        personasRepository.save(persona);

        PersonaResponse response = new PersonaResponse();
        response.setCodigo(1);
        response.setMensaje("Exito");
        BeanUtils.copyProperties(persona, response);
        return response;
    }

    @Override
    public GenericResponse eliminarPersona(EliminaPersonaRequest request) {
        return personasRepository.findByNombre(request.getNombre())
                .map(persona -> {
                    personasRepository.delete(persona);
                    return createResponse(0, "La persona ha sido eliminada correctamente");
                })
                .orElseGet(() -> createResponse(1, "La persona no existe "));
    }

    @Override
    public GenericResponse actualizarPersona(PersonasRequest request) {
        return personasRepository.findByNombre(request.getNombre())
                .map(persona -> updatePersona(persona, request))
                .orElseGet(() -> createResponse(1, "La persona no existe "));
    }

    private GenericResponse updatePersona(Personas persona, PersonasRequest request) {
        persona.setApellidoP(request.getApellidoP());
        persona.setApellidoMaterno(request.getApellidoMaterno());
        personasRepository.save(persona);
        return createResponse(0, "la persona ha sido actualizada correctamente");
    }

    private GenericResponse createResponse(int code, String message) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(code);
        response.setMensaje(message);
        return response;
    }
}
