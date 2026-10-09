package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;


public interface ClienteRepository extends JpaRepository<ClienteEntity, Long>, JpaSpecificationExecutor<ClienteEntity> {

    boolean existsByCurp(String curp);

    boolean existsByRfc(String rfc);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdNot(String correo, Long id);

    ClienteEntity findClienteById(Long id);

    ClienteEntity findByCurp(String curp);

    ClienteEntity findByRfc(String rfc);

    ClienteEntity findByCorreo(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClienteEntity c where c.id = :id")
    ClienteEntity findByIdForUpdate(@Param("id") Long id);
}
