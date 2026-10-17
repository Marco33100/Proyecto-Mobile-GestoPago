package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.UsuarioServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioMethodSecurityTest {
    private final UserRepository usuarios = mock(UserRepository.class);
    private final UserSessionRepository sesiones = mock(UserSessionRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("app.database.enabled=true")
            .withUserConfiguration(SecurityConfig.class)
            .withBean(UserRepository.class, () -> usuarios)
            .withBean(UserSessionRepository.class, () -> sesiones)
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(ApplicationEventPublisher.class, () -> mock(ApplicationEventPublisher.class));

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void sinSesionNoSePuedeInvocarLaBusquedaDelServicio() {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(UsuarioService.class)
                .buscar(new UsuarioBusquedaRequest())).isInstanceOf(AuthenticationCredentialsNotFoundException.class));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void clienteNoPuedeSaltarElControlDelController() {
        autenticar("ROLE_CLIENTE");
        runner.run(context -> assertThatThrownBy(() -> context.getBean(UsuarioService.class)
                .buscar(new UsuarioBusquedaRequest())).isInstanceOf(AccessDeniedException.class));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void ejecutivoPuedeEjecutarLaConsultaPaginada() {
        autenticar("ROLE_EJECUTIVO");
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        runner.run(context -> assertThat(context.getBean(UsuarioService.class).buscar(new UsuarioBusquedaRequest())
                .contenido()).isEmpty());
        verify(usuarios).findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
        verifyNoMoreInteractions(usuarios);
        verifyNoInteractions(sesiones);
    }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "actor", null, List.of(new SimpleGrantedAuthority(rol))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(UsuarioServiceImpl.class)
    static class SecurityConfig { }
}
