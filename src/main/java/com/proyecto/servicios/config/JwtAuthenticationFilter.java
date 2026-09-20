package com.proyecto.servicios.config;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ActiveSessionService activeSessionService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   ActiveSessionService activeSessionService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.activeSessionService = activeSessionService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            reject(request, response, null);
            return;
        }

        try {
            AuthenticatedUser user = jwtTokenService.validate(authorization.substring(7).trim());
            if (!activeSessionService.isActive(user.userId(), user.sessionId())) {
                throw new BadCredentialsException("La sesion fue reemplazada");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, exception);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Exception cause)
            throws IOException {
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException("JWT o sesion invalida", cause)
        );
    }
}
