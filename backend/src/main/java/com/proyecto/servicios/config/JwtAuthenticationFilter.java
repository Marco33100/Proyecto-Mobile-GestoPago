package com.proyecto.servicios.config;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.SesionActivaResponse;
import com.proyecto.servicios.entity.sf.RolUsuario;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.TransactionException;
import org.springframework.dao.DataAccessException;
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
    private final WebSessionCookies webSessionCookies;

    @Autowired
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   ActiveSessionService activeSessionService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                   WebSessionCookies webSessionCookies) {
        this.jwtTokenService = jwtTokenService;
        this.activeSessionService = activeSessionService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.webSessionCookies = webSessionCookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod())
                && WebSessionCookies.LOGIN_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token;
        if (StringUtils.hasText(authorization)) {
            // App movil: el JWT viaja en Authorization y tiene prioridad sobre la cookie.
            if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
                reject(request, response, null);
                return;
            }
            token = authorization.substring(7).trim();
        } else {
            // Cliente web: el JWT viaja en una cookie HttpOnly.
            token = webSessionCookies.sessionToken(request).orElse(null);
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        try {
            AuthenticatedUser user = jwtTokenService.validate(token);
            RolUsuario rol = activeSessionService.rolVigente(user.userId(), user.sessionId());
            if (rol == null) {
                // Distingue "entraste en otro dispositivo" de "expiro o cerraste sesion" para avisar al usuario.
                var nueva = activeSessionService.sesionVigente(user.userId())
                        .filter(s -> !s.getSessionId().equals(user.sessionId()));
                if (nueva.isPresent()) {
                    SecurityContextHolder.clearContext();
                    authenticationEntryPoint.sessionReplaced(request, response, new SesionActivaResponse(
                            nueva.get().getPlataforma(), nueva.get().getUpdatedAt()));
                    return;
                }
                throw new BadCredentialsException("La sesion expiro o fue cerrada");
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, exception);
            return;
        } catch (AuthPersistenceException | TransactionException | DataAccessException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.serviceUnavailable(request, response);
            return;
        }
        // Las excepciones del controlador no deben tratarse como errores del JWT.
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Exception cause)
            throws IOException {
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException("JWT o sesion invalida", cause)
        );
    }
}
