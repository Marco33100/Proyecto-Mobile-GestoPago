package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.Personas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonasRepository  extends JpaRepository<Personas, Integer> {


    Optional<Personas> findByNombre(String nombre);
}
