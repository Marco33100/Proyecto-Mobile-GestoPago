package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CuentaRepository extends JpaRepository<CuentaEntity, UUID> {

    List<CuentaEntity> findAllByClienteId(Long clienteId);

    List<CuentaEntity> findAllByClienteIdIn(Collection<Long> clienteIds);

    CuentaEntity findByNumeroCuenta(String numeroCuenta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaEntity c where c.numeroCuenta = :numeroCuenta")
    CuentaEntity findByNumeroCuentaForUpdate(@Param("numeroCuenta") String numeroCuenta);

    Page<CuentaEntity> findAllByActivaTrue(Pageable pageable);

    boolean existsByNumeroCuenta(String numeroCuenta);

    @Modifying(flushAutomatically = true)
    @Query("update CuentaEntity c set c.activa = false, c.fechaActualizacion = :fecha, "
            + "c.version = c.version + 1 where c.cliente.id = :clienteId and c.activa = true")
    int desactivarTodasPorCliente(@Param("clienteId") Long clienteId, @Param("fecha") Instant fecha);
}
