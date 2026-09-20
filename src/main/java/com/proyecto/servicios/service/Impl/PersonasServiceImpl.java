package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.service.PersonaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class PersonasServiceImpl implements PersonaService {
    @Autowired
    private PersonasRepository personasRepository;
    @Override
    public PersonaResponse creaPersona(PersonasRequest personasRequest) {
        PersonaResponse person=new PersonaResponse();
     Personas persona=new Personas();
     persona.setNombre(personasRequest.getNombre());
     persona.setApellidoMaterno(personasRequest.getApellidoMaterno());
     persona.setApellidoP(personasRequest.getApellidoP());
     personasRepository.save(persona);
     person.setCodigo(1);
     person.setMensaje("Exito");
     BeanUtils.copyProperties(persona,person);

     return person;
    }

    @Override
    public GenericResponse eliminaPersona(EliminaPersonaRequest eliminaPersonaRequest) {
        GenericResponse genericResponse=new GenericResponse();

        Optional<Personas> existePersona=personasRepository.findByNombre(eliminaPersonaRequest.getNombre());
        if(existePersona.isPresent()){
            Personas personaElimina=existePersona.get();
            personasRepository.delete(personaElimina);
            genericResponse.setCodigo(0);
            genericResponse.setMensaje("La persona ha sido eliminada correctamente");

        }else{
            genericResponse.setCodigo(1);
            genericResponse.setMensaje("La persona no existe ");
        }
       return genericResponse;

    }

    @Override
    public GenericResponse actualizaPersona(PersonasRequest personasRequest) {
        GenericResponse genericResponse=new GenericResponse();
        Optional<Personas> existePersona=personasRepository.findByNombre(personasRequest.getNombre());
        if(existePersona.isPresent()){
            Personas personaActualiza=existePersona.get();
            personaActualiza.setApellidoP(personasRequest.getApellidoP());
            personaActualiza.setApellidoMaterno(personasRequest.getApellidoMaterno());
            personasRepository.save(personaActualiza);
            genericResponse.setCodigo(0);
            genericResponse.setMensaje("la persona ha sido actualizada correctamente");

        }else{
            genericResponse.setCodigo(1);
            genericResponse.setMensaje("La persona no existe ");
        }
        return genericResponse;
    }
}
