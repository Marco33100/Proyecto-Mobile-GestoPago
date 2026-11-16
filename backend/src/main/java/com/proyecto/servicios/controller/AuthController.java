package com.proyecto.servicios.controller;

import com.proyecto.servicios.config.WebSessionCookies;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CsrfTokenResponse;
import com.proyecto.servicios.model.auth.UserProfileResponse;
import com.proyecto.servicios.model.GenericResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Autenticacion", description = "Acceso de clientes y ejecutivos")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final WebSessionCookies webSessionCookies;

    @Autowired
    public AuthController(AuthService authService, LoginAttemptLimiter loginAttemptLimiter,
                          WebSessionCookies webSessionCookies) {
        this.authService = authService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.webSessionCookies = webSessionCookies;
    }

    @PostMapping("/login")
    @Operation(summary = "Valida credenciales y devuelve una sesion JWT",
            description = "Con X-Auth-Mode: cookie (cliente web) el JWT se entrega en una cookie HttpOnly "
                    + "y no en el cuerpo de la respuesta.")
    public ResponseEntity<GenericResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @Parameter(description = "cookie para el cliente web")
            @RequestHeader(value = WebSessionCookies.AUTH_MODE_HEADER, required = false) String authMode) {
        loginAttemptLimiter.checkAccount(request.email());
        LoginResponse result = authService.login(request);
        loginAttemptLimiter.loginSucceeded(request.email());
        if (!webSessionCookies.cookieModeRequested(authMode)) {
            return ResponseEntity.ok(GenericResponse.exito("Inicio de sesion correcto", result));
        }
        LoginResponse sinToken = new LoginResponse(null, result.tokenType(), result.expiresAt(), result.user());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        webSessionCookies.session(result.accessToken(), result.expiresAt()).toString())
                // Rota el token CSRF: el cliente pide uno nuevo en /api/auth/csrf tras el login.
                .header(HttpHeaders.SET_COOKIE, webSessionCookies.expiredCsrf().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(GenericResponse.exito("Inicio de sesion correcto", sinToken));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoca la sesion presentada sin afectar un nuevo inicio de sesion")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser usuario) {
        authService.logout(usuario);
        // Si la sesion venia en cookie, el navegador la elimina; en movil no tiene efecto.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, webSessionCookies.expiredSession().toString())
                .header(HttpHeaders.SET_COOKIE, webSessionCookies.expiredCsrf().toString())
                .build();
    }

    @GetMapping("/sesion")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consulta el perfil de la sesion autenticada")
    public ResponseEntity<GenericResponse<UserProfileResponse>> sesion(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(GenericResponse.exito("Sesion consultada correctamente", authService.perfilActual(usuario)));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Entrega el token CSRF que el cliente web envia en operaciones que modifican datos")
    public ResponseEntity<GenericResponse<?>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(GenericResponse.error("La proteccion CSRF no esta disponible"));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(GenericResponse.exito("Token CSRF generado",
                        new CsrfTokenResponse(token.getHeaderName(), token.getToken())));
    }
}
