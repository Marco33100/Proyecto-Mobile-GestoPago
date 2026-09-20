package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByEmail(String email);
    boolean existsByIdentifier(String identifier);
    Optional<UserEntity> findByEmail(String email);
}
