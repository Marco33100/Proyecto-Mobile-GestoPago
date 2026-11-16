package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveSessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;
    private ActiveSessionService service;

    @BeforeEach
    void setUp() {
        service = new ActiveSessionService(sessionRepository);
    }

    @Test
    void sesionReemplazadaNoTieneRolVigente() {
        UUID userId = UUID.randomUUID();
        UUID oldSession = UUID.randomUUID();
        when(sessionRepository.encontrarRolVigente(eq(userId), eq(oldSession), any(Instant.class)))
                .thenReturn(null);

        assertThat(service.rolVigente(userId, oldSession)).isNull();
    }

    @Test
    void siPostgresFallaNoAutoriza() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.encontrarRolVigente(eq(userId), eq(sessionId), any(Instant.class)))
                .thenThrow(new DataAccessResourceFailureException("database offline"));
        assertThatThrownBy(() -> service.rolVigente(userId, sessionId)).isInstanceOf(AuthPersistenceException.class);
    }

    @Test
    void devuelveElRolVigenteDePostgres() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.encontrarRolVigente(eq(userId), eq(sessionId), any(Instant.class)))
                .thenReturn(RolUsuario.CLIENTE);

        assertThat(service.rolVigente(userId, sessionId)).isEqualTo(RolUsuario.CLIENTE);
    }

    @Test
    void revocaSoloLaSesionPresentada() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        service.revokeSession(user, session);
        verify(sessionRepository).eliminarSesionSiCoincide(user, session);
    }

    @Test
    void errorAlRevocarSeReportaComoFallaDePersistencia() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        when(sessionRepository.eliminarSesionSiCoincide(user, session))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.revokeSession(user, session))
                .isInstanceOf(AuthPersistenceException.class);
    }
}
