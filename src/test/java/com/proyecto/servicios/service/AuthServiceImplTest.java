package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.AccountEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.RegistrationException;
import com.proyecto.servicios.exception.auth.UserAlreadyExistsException;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.CachedUser;
import com.proyecto.servicios.model.auth.RegisterRequest;
import com.proyecto.servicios.model.auth.RegisterResponse;
import com.proyecto.servicios.repositorys.sf.AccountRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.Impl.AuthServiceImpl;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private AuthCacheStore authCacheStore;
    @Mock
    private ActiveSessionService activeSessionService;
    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthServiceImpl(
                userRepository, accountRepository, passwordEncoder, jwtTokenService,
                authCacheStore, activeSessionService
        );
    }

    @Test
    void registerCreatesUserAndLinkedAccount() {
        RegisterRequest request = new RegisterRequest(
                "  MARTI@EXAMPLE.COM ", "marti_01", "Password1", "Martin Perez"
        );

        RegisterResponse response = service.register(request);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        ArgumentCaptor<AccountEntity> accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        verify(accountRepository).saveAndFlush(accountCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        AccountEntity savedAccount = accountCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("marti@example.com");
        assertThat(passwordEncoder.matches("Password1", savedUser.getPasswordHash())).isTrue();
        assertThat(savedAccount.getUser()).isSameAs(savedUser);
        assertThat(response.userId()).isEqualTo(savedUser.getId());
        assertThat(response.accountId()).isEqualTo(savedAccount.getId());
    }

    @Test
    void registerRejectsAnExistingEmail() {
        when(userRepository.existsByEmail("marti@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "marti@example.com", "marti_01", "Password1", "Martin Perez"
        ))).isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsAnExistingIdentifier() {
        when(userRepository.existsByIdentifier("marti_01")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "other@example.com", "MARTI_01", "Password1", "Martin Perez"
        ))).isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Ya existe un usuario con ese identificador");

        verify(userRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerReportsDatabaseFailureWhenAccountCannotBeStored() {
        when(accountRepository.saveAndFlush(any()))
                .thenThrow(new DataAccessResourceFailureException("database offline"));

        assertThatThrownBy(() -> service.register(new RegisterRequest(
                "marti@example.com", "marti_01", "Password1", "Martin Perez"
        ))).isInstanceOf(RegistrationException.class)
                .hasMessage("No fue posible guardar el usuario y su cuenta");
    }

    @Test
    void loginReturnsTokenAndProfileForValidCredentials() {
        UserEntity user = user("marti@example.com", passwordEncoder.encode("Password1"));
        when(userRepository.findByEmail("marti@example.com")).thenReturn(Optional.of(user));
        Instant expiration = Instant.now().plusSeconds(3600);
        when(jwtTokenService.generate(any(CachedUser.class), any(UUID.class)))
                .thenReturn(new JwtTokenService.TokenResult("jwt-token", expiration));

        LoginResponse response = service.login(new LoginRequest("MARTI@example.com", "Password1"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiration);
        assertThat(response.user().email()).isEqualTo("marti@example.com");
        verify(authCacheStore).cacheUser(user);
        verify(activeSessionService).replaceActiveSession(
                org.mockito.ArgumentMatchers.eq(user.getId()), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(expiration)
        );
    }

    @Test
    void loginUsesRedisOnlyWhenPostgresIsUnavailable() {
        UUID userId = UUID.randomUUID();
        CachedUser cachedUser = new CachedUser(
                userId, "marti@example.com", "marti_01",
                passwordEncoder.encode("Password1"), "Martin Perez", true
        );
        when(userRepository.findByEmail("marti@example.com"))
                .thenThrow(new DataAccessResourceFailureException("database offline"));
        when(authCacheStore.findUser("marti@example.com")).thenReturn(Optional.of(cachedUser));
        Instant expiration = Instant.now().plusSeconds(3600);
        when(jwtTokenService.generate(any(CachedUser.class), any(UUID.class)))
                .thenReturn(new JwtTokenService.TokenResult("cached-jwt", expiration));

        LoginResponse response = service.login(new LoginRequest("marti@example.com", "Password1"));

        assertThat(response.accessToken()).isEqualTo("cached-jwt");
        assertThat(response.user().id()).isEqualTo(userId);
        verify(activeSessionService).replaceActiveSession(
                org.mockito.ArgumentMatchers.eq(userId), any(UUID.class),
                org.mockito.ArgumentMatchers.eq(expiration)
        );
    }

    @Test
    void loginDoesNotRevealWhetherEmailOrPasswordWasWrong() {
        UserEntity user = user("marti@example.com", passwordEncoder.encode("Password1"));
        when(userRepository.findByEmail("marti@example.com")).thenReturn(Optional.of(user));

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
}
