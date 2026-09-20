package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveSessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;
    @Mock
    private AuthCacheStore authCacheStore;
    private ActiveSessionService service;

    @BeforeEach
    void setUp() {
        service = new ActiveSessionService(sessionRepository, authCacheStore);
    }

    @Test
    void postgresSessionIsAuthoritativeWhenRedisStillContainsOldSession() {
        UUID userId = UUID.randomUUID();
        UUID oldSession = UUID.randomUUID();
        UUID newSession = UUID.randomUUID();
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(
                new UserSessionEntity(userId, newSession, Instant.now().plusSeconds(3600), Instant.now())
        ));

        assertThat(service.isActive(userId, oldSession)).isFalse();
    }

    @Test
    void fallsBackToRedisOnlyWhenPostgresIsUnavailable() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findById(userId))
                .thenThrow(new DataAccessResourceFailureException("database offline"));
        when(authCacheStore.findSessionId(userId)).thenReturn(Optional.of(sessionId));

        assertThat(service.isActive(userId, sessionId)).isTrue();
    }
}
