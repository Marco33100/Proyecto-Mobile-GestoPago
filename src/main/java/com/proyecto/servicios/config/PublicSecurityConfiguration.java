package com.proyecto.servicios.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Sin PostgreSQL solo se permiten salud y documentacion, nunca APIs sin autenticacion. */
@Configuration
@ConditionalOnProperty(
        name = "app.database.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class PublicSecurityConfiguration {

    @Bean
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> authenticationEntryPoint.serviceUnavailable(request, response)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
