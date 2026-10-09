package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.DomicilioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface DomicilioRepository extends JpaRepository<DomicilioEntity, UUID> {

    DomicilioEntity findByClienteId(Long clienteId);

    List<DomicilioEntity> findAllByClienteIdIn(Collection<Long> clienteIds);
}
