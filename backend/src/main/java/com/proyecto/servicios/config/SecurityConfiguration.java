package com.proyecto.servicios.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class SecurityConfiguration {
    private static final String EJECUTIVO = "EJECUTIVO";
    private static final String CLIENTE = "CLIENTE";

    @Bean
    public LoginRateLimitFilter loginRateLimitFilter(LoginAttemptLimiter limiter,
                                                    RestAuthenticationEntryPoint entryPoint) {
        return new LoginRateLimitFilter(limiter, entryPoint);
    }

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitRegistration(LoginRateLimitFilter filter) {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Se ejecuta solo dentro de Spring Security, no dos veces.
        return registration;
    }

    /**
     * CORS solo para los origenes del cliente web configurados. Sin origenes, no se
     * registra ninguna regla y el navegador bloquea las llamadas de otros sitios.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebSessionProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.getAllowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization",
                properties.getCsrfHeaderName(), WebSessionCookies.AUTH_MODE_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   LoginRateLimitFilter loginRateLimitFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   WebSessionProperties webSessionProperties,
                                                   WebSessionCookies webSessionCookies,
                                                   CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieName(webSessionProperties.getCsrfCookieName());
        csrfRepository.setHeaderName(webSessionProperties.getCsrfHeaderName());
        csrfRepository.setCookiePath(WebSessionCookies.COOKIE_PATH);
        // El cliente web obtiene el token en /api/auth/csrf; la cookie no necesita ser legible por JS.
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(webSessionProperties.isCookieSecure())
                .sameSite(webSessionProperties.getCookieSameSite()));

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // La app movil usa Bearer (sin CSRF). El cliente web usa cookie HttpOnly y debe
                // enviar el token CSRF en cada operacion que modifica datos.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .requireCsrfProtectionMatcher(webSessionCookies::requiresCsrf)
                        // Con sesion stateless cada peticion "se autentica" de nuevo; la estrategia
                        // por defecto borraria la cookie CSRF en cada una. El token se rota solo
                        // al iniciar sesion (AuthController expira la cookie anterior).
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler((request, response, exception) ->
                                        authenticationEntryPoint.forbidden(request, response)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login", "/api/auth/login/verificar", "/api/auth/csrf",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health", "/error"
                        ).permitAll()
                        .requestMatchers("/api/auth/register").denyAll()
                        .requestMatchers("/api/usuarios/busqueda").hasRole(EJECUTIVO)
                        .requestMatchers("/api/pagos/conciliacion", "/api/pagos/conciliacion/**").hasRole(EJECUTIVO)
                        .requestMatchers("/api/pagos", "/api/pagos/**").hasRole(CLIENTE)
                        .requestMatchers("/api/cuentas/activas/consulta").hasRole(EJECUTIVO)
                        .requestMatchers(HttpMethod.POST, "/api/cuentas/mias/consulta").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.POST, "/api/cuentas/consulta",
                                "/api/cuentas/saldo/consulta").hasAnyRole(EJECUTIVO, CLIENTE)
                        .requestMatchers("/api/clientes", "/api/clientes/**",
                                "/api/cuentas", "/api/cuentas/**").hasRole(EJECUTIVO)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }
}
