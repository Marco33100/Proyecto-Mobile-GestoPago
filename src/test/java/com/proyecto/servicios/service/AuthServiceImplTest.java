package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.CachedUser;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.Impl.AuthServiceImpl;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Test
    void logoutDelegaSoloUsuarioYSesionDelPrincipal() {
        var principal = new com.proyecto.servicios.model.auth.AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "uno@example.com", "uno@example.com");
        service.logout(principal);
        verify(activeSessionService).revokeSession(principal.userId(), principal.sessionId());
        org.mockito.Mockito.verifyNoInteractions(userRepository, jwtTokenService);
    }

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private ActiveSessionService activeSessionService;
    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthServiceImpl(
                userRepository, passwordEncoder, jwtTokenService,
                activeSessionService
        );
    }

    @Test
    void loginReturnsTokenAndProfileForValidCredentials() {
        UserEntity user = user("marti@example.com", passwordEncoder.encode("Password1"));
        when(userRepository.findByEmailForUpdate("marti@example.com")).thenReturn(user);
        Instant expiration = Instant.now().plusSeconds(3600);
        when(jwtTokenService.generate(any(CachedUser.class), any(UUID.class)))
                .thenReturn(new JwtTokenService.TokenResult("jwt-token", expiration));

        LoginResponse response = service.login(new LoginRequest("MARTI@example.com", "Password1"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiration);
        assertThat(response.user().email()).isEqualTo("marti@example.com");
        verify(activeSessionService).replaceActiveSession(
                org.mockito.ArgumentMatchers.eq(user.getId()), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(expiration)
        );
    }

    @Test
    void loginDeniegaAccesoSiNoPuedeVerificarPostgres() {
        when(userRepository.findByEmailForUpdate("marti@example.com"))
                .thenThrow(new DataAccessResourceFailureException("database offline"));
        assertThatThrownBy(() -> service.login(new LoginRequest("marti@example.com", "Password1")))
                .isInstanceOf(AuthPersistenceException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtTokenService, activeSessionService);
    }

    @Test
    void loginDoesNotRevealWhetherEmailOrPasswordWasWrong() {
        UserEntity user = user("marti@example.com", passwordEncoder.encode("Password1"));
        when(userRepository.findByEmailForUpdate("marti@example.com")).thenReturn(user);

        assertThatThrownBy(() -> service.login(new LoginRequest("marti@example.com", "Wrong1xx")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("El correo o la contrasena son incorrectos");
    }

    private UserEntity user(String email, String passwordHash) {
        return new UserEntity(
                UUID.randomUUID(), email, "marti_01", passwordHash,
                "Martin Perez", true, Instant.now()
        );
    }

    @Test
    void deniegaLoginDeUsuarioInactivo() {
        UserEntity user = user("marti@example.com", passwordEncoder.encode("Password1"));
        user.desactivar();
        when(userRepository.findByEmailForUpdate("marti@example.com")).thenReturn(user);
        assertThatThrownBy(() -> service.login(new LoginRequest("marti@example.com", "Password1")))
                .isInstanceOf(UsuarioInactivoException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtTokenService, activeSessionService);
    }

    @Test
    void deniegaLoginDeUsuarioInexistente() {
        assertThatThrownBy(() -> service.login(new LoginRequest("nadie@example.com", "Password1")))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        org.mockito.Mockito.verifyNoInteractions(jwtTokenService, activeSessionService);
    }
}
