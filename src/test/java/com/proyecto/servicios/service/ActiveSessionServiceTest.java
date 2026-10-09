package com.proyecto.servicios.service;

import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.context.ApplicationEventPublisher;
import com.proyecto.servicios.model.auth.SesionRevocadaEvent;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveSessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private AuthCacheStore authCacheStore;
    @Mock
    private ApplicationEventPublisher events;
    private ActiveSessionService service;

    @BeforeEach
    void setUp() {
        service = new ActiveSessionService(sessionRepository, authCacheStore, events);
    }

    @Test
    void postgresSessionIsAuthoritativeWhenRedisStillContainsOldSession() {
        UUID userId = UUID.randomUUID();
        UUID oldSession = UUID.randomUUID();
        when(sessionRepository.encontrarRolVigente(eq(userId), eq(oldSession), any(Instant.class)))
                .thenReturn(null);

        assertThat(service.isActive(userId, oldSession)).isFalse();
        verifyNoInteractions(authCacheStore);
    }

    @Test
    void nuncaAutorizaDesdeRedisSiPostgresFalla() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.encontrarRolVigente(eq(userId), eq(sessionId), any(Instant.class)))
                .thenThrow(new DataAccessResourceFailureException("database offline"));
        assertThatThrownBy(() -> service.isActive(userId, sessionId)).isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(authCacheStore);
    }

    @Test
    void revocaSoloLaSesionPresentadaYPublicaEvento() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        when(sessionRepository.eliminarSesionSiCoincide(user, session)).thenReturn(1);
        service.revokeSession(user, session);
        verify(events).publishEvent(new SesionRevocadaEvent(user, session));
        verifyNoInteractions(authCacheStore);
    }

    @Test
    void logoutAntiguoNoInvalidaUnaSesionNueva() {
        UUID user = UUID.randomUUID();
        UUID oldSession = UUID.randomUUID();
        when(sessionRepository.eliminarSesionSiCoincide(user, oldSession)).thenReturn(0);
        service.revokeSession(user, oldSession);
        verifyNoInteractions(events, authCacheStore);
    }

    @Test
    void errorAlRevocarNoPublicaEventoNiBorraCache() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        when(sessionRepository.eliminarSesionSiCoincide(user, session))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.revokeSession(user, session))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(events, authCacheStore);
    }
}
