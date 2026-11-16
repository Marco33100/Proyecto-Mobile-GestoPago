package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.PlataformaSesion;
import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sesion unica por usuario. PostgreSQL es la unica fuente: cada peticion la consulta ahi. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ActiveSessionService {

    private final UserSessionRepository sessionRepository;

    @Autowired
    public ActiveSessionService(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public void revokeSession(UUID userId, UUID sessionId) {
        try {
            sessionRepository.eliminarSesionSiCoincide(userId, sessionId);
        } catch (DataAccessException exception) {
            log.error("No fue posible revocar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible cerrar la sesion del usuario", exception);
        }
    }

    /** Sesion guardada del usuario si aun no vence (la de cualquier dispositivo). */
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public Optional<UserSessionEntity> sesionVigente(UUID userId) {
        try {
            return sessionRepository.findById(userId).filter(s -> s.getExpiresAt().isAfter(Instant.now()));
        } catch (DataAccessException exception) {
            log.error("No fue posible consultar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public void replaceActiveSession(UUID userId, UUID sessionId, Instant expiresAt, PlataformaSesion plataforma) {
        try {
            sessionRepository.saveAndFlush(new UserSessionEntity(userId, sessionId, expiresAt, Instant.now(), plataforma));
        } catch (DataAccessException exception) {
            log.error("No fue posible guardar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible crear la sesion del usuario", exception);
        }
    }

    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public RolUsuario rolVigente(UUID userId, UUID sessionId) {
        try {
            return sessionRepository.encontrarRolVigente(userId, sessionId, Instant.now());
        } catch (DataAccessException exception) {
            log.error("No fue posible verificar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }
}
