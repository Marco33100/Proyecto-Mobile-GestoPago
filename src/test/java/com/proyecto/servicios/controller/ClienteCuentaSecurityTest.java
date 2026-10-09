package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClienteCuentaSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ActiveSessionService sessions;
    private ClienteService clientes;
    private CuentaService cuentas;
    private ClienteRegistroService registro;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("app.database.enabled", "true")));
        context.register(SecurityConfiguration.class, SecurityTestConfiguration.class,
                LoginRateLimitProperties.class, com.proyecto.servicios.service.Impl.LoginAttemptLimiter.class);
        context.refresh();
        sessions = context.getBean(ActiveSessionService.class);
        when(context.getBean(JwtTokenService.class).validate("token")).thenReturn(
                new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "uno@example.com", "uno"));
        clientes = mock(ClienteService.class);
        cuentas = mock(CuentaService.class);
        registro = mock(ClienteRegistroService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, registro), new CuentaController(cuentas))
                .setControllerAdvice(new ClienteExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach void tearDown() { context.close(); }

    @Test
    void rutasApiNoPermitenAccesoSinSesion() throws Exception {
        for (String route : List.of("/api/clientes", "/api/clientes/1", "/api/cuentas", "/api/cuentas/CTA-123")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void clienteNoAdministraClientesNiCuentasEnNingunaRuta() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            String route = method == HttpMethod.PUT || method == HttpMethod.DELETE ? "/api/clientes/1" : "/api/clientes";
            mvc.perform(request(method, route).header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("AUTH_FORBIDDEN"))
                    .andExpect(jsonPath("path").value(route));
        }
        for (String suffix : List.of("/1", "/curp/MALM900101HDFRPR01", "/rfc/MALM900101AB1",
                "/correo?correo=uno@example.com", "/cuenta/CTA-123")) {
            mvc.perform(get("/api/clientes" + suffix).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        for (String route : List.of("/api/cuentas/CTA-123", "/api/cuentas/CTA-123/saldo", "/api/cuentas/activas")) {
            mvc.perform(get(route).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void soloEjecutivoPuedeDesactivarUnaCuenta() throws Exception {
        mvc.perform(delete("/api/cuentas/CTA-123")).andExpect(status().isUnauthorized());
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        mvc.perform(delete("/api/cuentas/CTA-123").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cuentas);
    }

    @Test
    void ejecutivoPuedeConsultarYCrearSoloConPayloadValido() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").header("Authorization", "Bearer token")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_CLIENT_DATA"));
        mvc.perform(get("/api/cuentas/CTA-123").header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(delete("/api/cuentas/CTA-123").header("Authorization", "Bearer token")).andExpect(status().isOk());
        verify(clientes).consultarTodos(0, 20, null, null, null);
        verify(cuentas).consultarPorNumeroCuenta("CTA-123");
        verify(cuentas).desactivar("CTA-123");
        verifyNoInteractions(registro);
    }

    @Test
    void rutasSinPrefijoApiYaNoEstanExpuestas() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        for (String route : List.of("/clientes", "/clientes/1", "/clientes/correo",
                "/cuentas/CTA-123", "/cuentas/CTA-123/saldo", "/cuentas/activas")) {
            mvc.perform(get(route).header("Authorization", "Bearer token"))
                    .andExpect(status().isNotFound());
        }
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            String route = method == HttpMethod.POST ? "/clientes" : "/clientes/1";
            mvc.perform(request(method, route).header("Authorization", "Bearer token")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(delete("/cuentas/CTA-123").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void rutasAntiguasDePersonasYaNoEstanExpuestas() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        for (String route : List.of("/personas", "/personasActualiza", "/personasElimina")) {
            mvc.perform(post(route).header("Authorization", "Bearer token")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void retirarRolBloqueaElMismoTokenEnLaSiguientePeticion() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO, RolUsuario.CLIENTE);
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(get("/api/clientes").header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        verify(clientes).consultarTodos(0, 20, null, null, null);
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    static class SecurityTestConfiguration {
        @Bean RestAuthenticationEntryPoint entryPoint() {
            return new RestAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());
        }
        @Bean JwtTokenService jwt() { return mock(JwtTokenService.class); }
        @Bean ActiveSessionService sessions() { return mock(ActiveSessionService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenService jwt, ActiveSessionService sessions,
                                                RestAuthenticationEntryPoint entryPoint) {
            return new JwtAuthenticationFilter(jwt, sessions, entryPoint);
        }
    }
}
