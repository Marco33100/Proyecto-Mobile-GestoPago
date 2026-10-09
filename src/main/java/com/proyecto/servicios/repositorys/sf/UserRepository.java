package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.entity.sf.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByRolAndEnabledTrue(RolUsuario rol);
    @Query(value = "select exists(select 1 from app_users where lower(trim(email)) = :email)",
            nativeQuery = true)
    boolean existsByEmail(@Param("email") String email);
    boolean existsByIdentifier(String identifier);

    // Dos indices independientes, una sola ida a la BD y sin cargar entidades.
    @Query(value = """
            select exists(select 1 from app_users where lower(trim(email)) = :email)
                or exists(select 1 from app_users where identifier = :email)
            """, nativeQuery = true)
    boolean existsByEmailOrIdentifier(@Param("email") String email);
    @Query("select u from UserEntity u where lower(trim(u.email)) = :email")
    Optional<UserEntity> findByEmail(@Param("email") String email);
    UserEntity findUserByClienteId(Long clienteId);

    UserEntity findUserById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where lower(trim(u.email)) = :email")
    UserEntity findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    UserEntity findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.cliente.id = :clienteId")
    UserEntity findByClienteIdForUpdate(@Param("clienteId") Long clienteId);
}
