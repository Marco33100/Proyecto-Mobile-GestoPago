package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Autenticacion", description = "Acceso de clientes y ejecutivos")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(AuthService authService, LoginAttemptLimiter loginAttemptLimiter) {
        this.authService = authService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @PostMapping("/login")
    @Operation(summary = "Valida credenciales y devuelve una sesion JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        loginAttemptLimiter.checkAccount(request.email());
        LoginResponse result = authService.login(request);
        loginAttemptLimiter.loginSucceeded(request.email());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoca la sesion presentada sin afectar un nuevo inicio de sesion")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser usuario) {
        authService.logout(usuario);
        return ResponseEntity.noContent().build();
    }
}
