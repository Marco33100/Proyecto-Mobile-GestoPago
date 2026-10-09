package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.model.auth.SesionRevocadaEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ActiveSessionService {

    private final UserSessionRepository sessionRepository;
    private final AuthCacheStore authCacheStore;
    private final ApplicationEventPublisher events;

    @Autowired
    public ActiveSessionService(UserSessionRepository sessionRepository, AuthCacheStore authCacheStore,
                                ApplicationEventPublisher events) {
        this.sessionRepository = sessionRepository;
        this.authCacheStore = authCacheStore;
        this.events = events;
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public void revokeSession(UUID userId, UUID sessionId) {
        try {
            if (sessionRepository.eliminarSesionSiCoincide(userId, sessionId) > 0) {
                events.publishEvent(new SesionRevocadaEvent(userId, sessionId));
            }
        } catch (DataAccessException exception) {
            log.error("No fue posible revocar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible cerrar la sesion del usuario", exception);
        }
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public void replaceActiveSession(UUID userId, UUID sessionId, Instant expiresAt) {
        try {
            sessionRepository.saveAndFlush(new UserSessionEntity(userId, sessionId, expiresAt, Instant.now()));
        } catch (DataAccessException exception) {
            log.error("No fue posible guardar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible crear la sesion del usuario", exception);
        }

        try {
            authCacheStore.replaceSession(userId, sessionId, expiresAt);
        } catch (AuthCacheException exception) {
            log.warn("No fue posible copiar la sesion en Redis");
        }
    }

    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public boolean isActive(UUID userId, UUID sessionId) {
        return rolVigente(userId, sessionId) != null;
    }

    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public RolUsuario rolVigente(UUID userId, UUID sessionId) {
        // Redis nunca sustituye la comprobacion de usuario, cliente y sesion en PostgreSQL.
        try {
            return sessionRepository.encontrarRolVigente(userId, sessionId, Instant.now());
        } catch (DataAccessException exception) {
            log.error("No fue posible verificar la sesion en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }
}
