package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.auth.LoginRateLimitException;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptLimiter limiter;
    private final RestAuthenticationEntryPoint entryPoint;

    public LoginRateLimitFilter(LoginAttemptLimiter limiter, RestAuthenticationEntryPoint entryPoint) {
        this.limiter = limiter;
        this.entryPoint = entryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !WebSessionCookies.LOGIN_PATHS.contains(
                        request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        try {
            // IP real del cliente: en Render la resuelve Tomcat desde el proxy interno
            // (server.forward-headers-strategy=native); un X-Forwarded-For del cliente no se acepta.
            limiter.checkAddress(request.getRemoteAddr());
        } catch (LoginRateLimitException exception) {
            entryPoint.tooManyRequests(request, response, exception.getRetryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }
}
