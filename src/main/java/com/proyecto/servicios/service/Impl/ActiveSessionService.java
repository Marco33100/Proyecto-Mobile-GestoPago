package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ActiveSessionService {

    private final UserSessionRepository sessionRepository;
    private final AuthCacheStore authCacheStore;

    @Autowired
    public ActiveSessionService(UserSessionRepository sessionRepository, AuthCacheStore authCacheStore) {
        this.sessionRepository = sessionRepository;
        this.authCacheStore = authCacheStore;
    }

    public void replaceActiveSession(UUID userId, UUID sessionId, Instant expiresAt) {
        RuntimeException databaseFailure = null;
        RuntimeException cacheFailure = null;
        boolean stored = false;

        try {
            sessionRepository.saveAndFlush(new UserSessionEntity(userId, sessionId, expiresAt, Instant.now()));
            stored = true;
        } catch (DataAccessException exception) {
            databaseFailure = exception;
            log.warn("PostgreSQL no estuvo disponible al reemplazar la sesion activa");
        }

        try {
            authCacheStore.replaceSession(userId, sessionId, expiresAt);
            stored = true;
        } catch (AuthCacheException exception) {
            cacheFailure = exception;
            log.warn("Redis no estuvo disponible al reemplazar la sesion activa");
        }

        if (!stored) {
            Throwable cause = databaseFailure != null ? databaseFailure : cacheFailure;
            throw new AuthPersistenceException("No fue posible crear la sesion del usuario", cause);
        }
    }

    public boolean isActive(UUID userId, UUID sessionId) {
        // PostgreSQL es la fuente de verdad. Así, si un login reemplazó la sesión
        // en la base pero Redis falló, el token anterior queda invalidado de inmediato.
        try {
            return sessionRepository.findById(userId)
                    .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                    .filter(session -> session.getSessionId().equals(sessionId))
                    .map(session -> restoreCache(userId, sessionId, session.getExpiresAt()))
                    .orElse(false);
        } catch (DataAccessException exception) {
            log.warn("PostgreSQL no estuvo disponible al validar la sesion; se consultara Redis");
            try {
                return authCacheStore.findSessionId(userId)
                        .filter(sessionId::equals)
                        .isPresent();
            } catch (AuthCacheException cacheException) {
                log.warn("Redis tampoco estuvo disponible al validar la sesion");
                return false;
            }
        }
    }

    private boolean restoreCache(UUID userId, UUID sessionId, Instant expiresAt) {
        try {
            authCacheStore.replaceSession(userId, sessionId, expiresAt);
        } catch (AuthCacheException exception) {
            log.debug("No fue posible reconstruir la sesion en Redis");
        }
        return true;
    }
}
