package com.proyecto.servicios.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class SecurityConfiguration {

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   LoginRateLimitFilter loginRateLimitFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler((request, response, exception) ->
                                        authenticationEntryPoint.forbidden(request, response)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health", "/error"
                        ).permitAll()
                        .requestMatchers("/api/auth/register").denyAll()
                        .requestMatchers("/api/clientes", "/api/clientes/**",
                                "/api/cuentas", "/api/cuentas/**").hasRole("EJECUTIVO")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }
}
