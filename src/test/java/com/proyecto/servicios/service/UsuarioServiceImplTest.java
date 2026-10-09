package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.*;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {
    @Mock private UserRepository usuarios;
    @Mock private UserSessionRepository sesiones;
    @Mock private ApplicationEventPublisher events;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioServiceImpl service;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        service = new UsuarioServiceImpl(usuarios, sesiones, encoder, events);
        usuario = new UserEntity(UUID.randomUUID(), "uno@example.com", "uno", encoder.encode("Actual123!"),
                "Marco Martinez", true, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void consultaSoloPerfilSinCredenciales() throws Exception {
        when(usuarios.findUserById(usuario.getId())).thenReturn(usuario);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(
                service.consultar(usuario.getId(), usuario.getId()));
        assertThat(json).contains("uno@example.com").doesNotContain("password", "hash", "Actual123!");
    }

    @Test
    void noPuedeConsultarNiCambiarOtroUsuario() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.consultar(usuario.getId(), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cambiarContrasena(usuario.getId(), actor,
                new CambiarContrasenaRequest("Actual123!", "Nueva456!"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void cambioContrasenaUsaBcryptAuditoriaYRevocaSesion() {
        when(usuarios.findByIdForUpdate(usuario.getId())).thenReturn(usuario);
        service.cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Actual123!", "Nueva456!"));
        assertThat(encoder.matches("Nueva456!", usuario.getPasswordHash())).isTrue();
        assertThat(encoder.matches("Actual123!", usuario.getPasswordHash())).isFalse();
        assertThat(usuario.getUpdatedAt()).isAfter(usuario.getCreatedAt());
        verify(usuarios).saveAndFlush(usuario);
        verify(sesiones).deleteById(usuario.getId());
        verify(events).publishEvent(new UsuarioClienteModificadoEvent(
                usuario.getId(), usuario.getEmail(), usuario.getEmail()));
    }

    @Test
    void contrasenaActualIncorrectaNoModificaNada() {
        when(usuarios.findByIdForUpdate(usuario.getId())).thenReturn(usuario);
        assertThatThrownBy(() -> service.cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Incorrecta1!", "Nueva456!")))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void nuevaContrasenaSinEspecialSeRechaza() {
        assertThatThrownBy(() -> service.cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Actual123!", "Nueva456")))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void usuarioNoEncontradoTieneExcepcionEspecifica() {
        assertThatThrownBy(() -> service.consultar(usuario.getId(), usuario.getId()))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void usuarioInactivoNoPuedeCambiarContrasena() {
        usuario.desactivar();
        when(usuarios.findByIdForUpdate(usuario.getId())).thenReturn(usuario);
        assertThatThrownBy(() -> service.cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Actual123!", "Nueva456!")))
                .isInstanceOf(UsuarioInactivoException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void falloPostgresNoSeOcultaComoExito() {
        when(usuarios.findByIdForUpdate(usuario.getId()))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThatThrownBy(() -> service.cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Actual123!", "Nueva456!")))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }
}
