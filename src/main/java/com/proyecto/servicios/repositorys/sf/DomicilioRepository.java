package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.DomicilioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DomicilioRepository extends JpaRepository<DomicilioEntity, UUID> {
}
