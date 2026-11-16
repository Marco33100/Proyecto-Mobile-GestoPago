package com.proyecto.servicios.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Crea y lee la cookie HttpOnly que transporta el JWT del cliente web. */
@Component
public class WebSessionCookies {
    /** Valor del encabezado {@code X-Auth-Mode} con el que el cliente web pide la cookie. */
    public static final String AUTH_MODE_HEADER = "X-Auth-Mode";
    public static final String COOKIE_MODE = "cookie";
    static final String COOKIE_PATH = "/api";
    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final WebSessionProperties properties;

    @Autowired
    public WebSessionCookies(WebSessionProperties properties) {
        this.properties = properties;
    }

    public boolean cookieModeRequested(String authMode) {
        return COOKIE_MODE.equalsIgnoreCase(authMode);
    }

    public ResponseCookie session(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return base(properties.getCookieName(), token)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    public ResponseCookie expiredSession() {
        return base(properties.getCookieName(), "").maxAge(Duration.ZERO).build();
    }

    public ResponseCookie expiredCsrf() {
        return base(properties.getCsrfCookieName(), "").maxAge(Duration.ZERO).build();
    }

    public Optional<String> sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (properties.getCookieName().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * CSRF solo aplica a operaciones que modifican datos autenticadas con la cookie,
     * porque el navegador la envia de forma automatica. Un JWT en Authorization no
     * viaja solo, asi que la app movil no necesita el token CSRF.
     */
    public boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        if (StringUtils.hasText(request.getHeader("Authorization"))) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/api/auth/login".equals(path)) {
            return false;
        }
        return sessionToken(request).isPresent();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(COOKIE_PATH);
    }
}
