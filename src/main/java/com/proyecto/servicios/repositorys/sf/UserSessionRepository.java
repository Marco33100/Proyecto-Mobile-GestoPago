package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {
}
