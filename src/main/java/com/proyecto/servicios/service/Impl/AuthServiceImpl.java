package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.CachedUser;
import com.proyecto.servicios.model.auth.UserProfileResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final ActiveSessionService activeSessionService;
    private final String dummyPasswordHash;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                           ActiveSessionService activeSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.activeSessionService = activeSessionService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public LoginResponse login(LoginRequest request) {
        if (request.password() == null || request.password().isBlank()
                || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        try {
            // El bloqueo serializa login, baja y cambio de contrasena del mismo usuario.
            UserEntity user = userRepository.findByEmailForUpdate(normalizeEmail(request.email()));
            if (user == null) {
                passwordEncoder.matches(request.password(), dummyPasswordHash);
                throw new UsuarioNoEncontradoException();
            }
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new InvalidCredentialsException();
            }
            if (!user.isEnabled() || (user.getCliente() != null && !user.getCliente().isActivo())) {
                throw new UsuarioInactivoException();
            }
            UUID sessionId = UUID.randomUUID();
            JwtTokenService.TokenResult token = jwtTokenService.generate(toCachedUser(user), sessionId);
            activeSessionService.replaceActiveSession(user.getId(), sessionId, token.expiresAt());
            return new LoginResponse(token.value(), "Bearer", token.expiresAt(),
                    new UserProfileResponse(user.getId(), user.getEmail(), user.getIdentifier(),
                            user.getFullName(), user.getRol()));
        } catch (DataAccessException exception) {
            log.error("No fue posible verificar el acceso en PostgreSQL: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }

    @Override
    public void logout(AuthenticatedUser usuario) {
        activeSessionService.revokeSession(usuario.userId(), usuario.sessionId());
    }

    private CachedUser toCachedUser(UserEntity user) {
        return new CachedUser(
                user.getId(), user.getEmail(), user.getIdentifier(), user.getPasswordHash(),
                user.getFullName(), user.isEnabled()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
