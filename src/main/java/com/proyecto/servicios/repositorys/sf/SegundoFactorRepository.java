package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.SegundoFactorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SegundoFactorRepository extends JpaRepository<SegundoFactorEntity, UUID> { }
