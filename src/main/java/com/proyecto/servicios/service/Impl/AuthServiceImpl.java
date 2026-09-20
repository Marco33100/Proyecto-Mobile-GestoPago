package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.AccountEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.RegistrationException;
import com.proyecto.servicios.exception.auth.UserAlreadyExistsException;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.CachedUser;
import com.proyecto.servicios.model.auth.RegisterRequest;
import com.proyecto.servicios.model.auth.RegisterResponse;
import com.proyecto.servicios.model.auth.UserProfileResponse;
import com.proyecto.servicios.repositorys.sf.AccountRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthCacheStore authCacheStore;
    private final ActiveSessionService activeSessionService;

    public AuthServiceImpl(UserRepository userRepository, AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                           AuthCacheStore authCacheStore, ActiveSessionService activeSessionService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authCacheStore = authCacheStore;
        this.activeSessionService = activeSessionService;
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedIdentifier = request.identifier().trim().toLowerCase(Locale.ROOT);

        try {
            validateUserDoesNotExist(normalizedEmail, normalizedIdentifier);

            Instant now = Instant.now();
            UserEntity user = new UserEntity(
                    UUID.randomUUID(),
                    normalizedEmail,
                    normalizedIdentifier,
                    passwordEncoder.encode(request.password()),
                    request.fullName().trim(),
                    true,
                    now
            );
            userRepository.saveAndFlush(user);

            AccountEntity account = new AccountEntity(
                    UUID.randomUUID(),
                    user,
                    createAccountNumber(),
                    "ACTIVE",
                    now
            );
            accountRepository.saveAndFlush(account);

            return new RegisterResponse(
                    user.getId(), account.getId(), account.getAccountNumber(),
                    "Usuario y cuenta creados correctamente"
            );
        } catch (UserAlreadyExistsException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            // Tambien protege contra registros simultaneos que superen la validacion previa.
            throw new UserAlreadyExistsException("El correo o identificador ya esta registrado");
        } catch (DataAccessException exception) {
            throw new RegistrationException("No fue posible guardar el usuario y su cuenta", exception);
        } catch (RuntimeException exception) {
            throw new RegistrationException("Ocurrio un error al registrar el usuario", exception);
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        CachedUser user = findUserWithCacheFallback(normalizeEmail(request.email()));
        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        UUID sessionId = UUID.randomUUID();
        JwtTokenService.TokenResult token = jwtTokenService.generate(user, sessionId);
        activeSessionService.replaceActiveSession(user.id(), sessionId, token.expiresAt());
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                new UserProfileResponse(user.id(), user.email(), user.identifier(), user.fullName())
        );
    }

    private CachedUser findUserWithCacheFallback(String normalizedEmail) {
        try {
            UserEntity user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(InvalidCredentialsException::new);
            try {
                authCacheStore.cacheUser(user);
            } catch (AuthCacheException exception) {
                log.warn("Redis no estuvo disponible para cachear el usuario autenticado");
            }
            return toCachedUser(user);
        } catch (InvalidCredentialsException exception) {
            throw exception;
        } catch (DataAccessException databaseException) {
            log.warn("PostgreSQL no estuvo disponible durante el login; se consultara Redis");
            try {
                return authCacheStore.findUser(normalizedEmail)
                        .orElseThrow(() -> new AuthPersistenceException(
                                "PostgreSQL no esta disponible y el usuario no existe en cache",
                                databaseException
                        ));
            } catch (AuthCacheException cacheException) {
                throw new AuthPersistenceException(
                        "No fue posible consultar las credenciales en PostgreSQL ni Redis",
                        cacheException
                );
            }
        }
    }

    private CachedUser toCachedUser(UserEntity user) {
        return new CachedUser(
                user.getId(), user.getEmail(), user.getIdentifier(), user.getPasswordHash(),
                user.getFullName(), user.isEnabled()
        );
    }

    private void validateUserDoesNotExist(String email, String identifier) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese correo");
        }
        if (userRepository.existsByIdentifier(identifier)) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese identificador");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String createAccountNumber() {
        return "ACC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }
}
