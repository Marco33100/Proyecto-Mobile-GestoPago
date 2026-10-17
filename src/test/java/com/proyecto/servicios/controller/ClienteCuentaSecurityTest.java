package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import java.math.BigDecimal;
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
import static com.proyecto.servicios.controller.ApiTestRequests.*;
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
    private AuthenticatedUser usuario;

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
        usuario = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "uno@example.com", "uno");
        when(context.getBean(JwtTokenService.class).validate("token")).thenReturn(usuario);
        clientes = mock(ClienteService.class);
        cuentas = mock(CuentaService.class);
        registro = mock(ClienteRegistroService.class);
        when(clientes.consultar(any())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        when(cuentas.consultarActivas(anyInt(), anyInt())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        when(cuentas.consultarPropias(anyInt(), anyInt())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, registro), new CuentaController(cuentas))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach void tearDown() { context.close(); }

    @Test
    void rutasApiNoPermitenAccesoSinSesion() throws Exception {
for (String route : List.of("/api/clientes/consulta", "/api/cuentas/consulta",
                "/api/cuentas/mias/consulta", "/api/cuentas/activas/consulta", "/api/cuentas/saldo/consulta")) {
            mvc.perform(postJson(route, Map.of())).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("codigo").value(2)).andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void clienteNoAdministraClientesNiCuentasEnNingunaRuta() throws Exception {
when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            mvc.perform(request(method, "/api/clientes").header("Authorization", "Bearer token")
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("codigo").value(2));
        }
        for (Map<String, Object> filtro : List.<Map<String, Object>>of(Map.of("id", 1), Map.of("rfc", "MALM"),
                Map.of("curp", "MALM"), Map.of("nombre", "Marco"), Map.of("correo", "uno@example.com"),
                Map.of("numeroCuenta", "CTA-0123456789ABCDEF0123"), Map.of())) {
            mvc.perform(postJson("/api/clientes/consulta", filtro).header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(postJson("/api/cuentas/activas/consulta", Map.of()).header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientes, cuentas, registro);
    }

    @Test
    void soloEjecutivoPuedeDesactivarUnaCuenta() throws Exception {
        mvc.perform(bajaCuenta("CTA-123")).andExpect(status().isUnauthorized());
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        mvc.perform(bajaCuenta("CTA-123").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cuentas);
    }

    @Test
    void cambiarNumeroPorElDeOtraCuentaValidaOEnviarUsuarioNoConcedeAcceso() throws Exception {
when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        for (String numero : List.of("CTA-0123456789ABCDEF0123", "CTA-FFFFFFFFFFFFFFFFFFFF")) {
            when(cuentas.consultarPorNumeroCuenta(numero)).thenThrow(new CuentaNoEncontradaException("No existe la cuenta solicitada"));
            when(cuentas.consultarSaldo(numero)).thenThrow(new CuentaNoEncontradaException("No existe la cuenta solicitada"));
            mvc.perform(cuentaConsulta(numero).header("Authorization", "Bearer token").param("usuarioId", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2));
            mvc.perform(saldoConsulta(numero).header("Authorization", "Bearer token"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2));
            mvc.perform(parcheCuenta(numero, "{\"activa\":false}").header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden());
            mvc.perform(bajaCuenta(numero).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
            mvc.perform(postJson("/api/clientes/consulta", Map.of("numeroCuenta", numero)).header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden());
            verify(cuentas).consultarPorNumeroCuenta(numero);
            verify(cuentas).consultarSaldo(numero);
        }
        verifyNoMoreInteractions(cuentas);
        verifyNoInteractions(clientes, registro);
    }

    @Test
    void clientePuedeLeerCuentaSaldoYListadoPropios() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        String numero = "CTA-0123456789ABCDEF0123";
        var cuenta = new CuentaResponse(UUID.randomUUID(), numero, BigDecimal.TEN, true);
        when(cuentas.consultarPorNumeroCuenta(numero)).thenReturn(cuenta);
        when(cuentas.consultarSaldo(numero)).thenReturn(new SaldoCuentaResponse(numero, BigDecimal.TEN));
        when(cuentas.consultarPropias(0, 20)).thenReturn(PaginaResponse.desde(new org.springframework.data.domain.PageImpl<>(List.of(cuenta))));
        mvc.perform(cuentaConsulta(numero).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.numeroCuenta").value(numero));
        mvc.perform(saldoConsulta(numero).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.saldo").value(10));
        mvc.perform(postJson("/api/cuentas/mias/consulta", Map.of()).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.contenido[0].numeroCuenta").value(numero));
        verify(cuentas).consultarPropias(0, 20);
    }

    @Test
    void listadoPersonalValidaPaginacionYNoAbreOperacionesDeEscritura() throws Exception {
when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        mvc.perform(postJson("/api/cuentas/mias/consulta", Map.of("pagina", -1, "tamanio", 101))
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mvc.perform(request(method, "/api/cuentas/mias/consulta").header("Authorization", "Bearer token"))
                    .andExpect(status().isForbidden());
        }
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        mvc.perform(postJson("/api/cuentas/mias/consulta", Map.of()).header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(postJson("/api/cuentas/activas/consulta", Map.of()).header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(cuentas).consultarActivas(0, 20);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void filtroJwtYServicioRealIgnoranIdentificadoresDePropietarioEnviadosPorCliente() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        CuentaRepository repository = mock(CuentaRepository.class);
        var service = new CuentaServiceImpl(repository, new CuentaProperties(), org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class));
        MockMvc mvcReal = MockMvcBuilders.standaloneSetup(new CuentaController(service))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        String numero = "CTA-FFFFFFFFFFFFFFFFFFFF";
        String usuarioAjeno = UUID.randomUUID().toString();
        for (String suffix : List.of("/consulta", "/saldo/consulta")) {
            mvcReal.perform(postJson("/api/cuentas" + suffix, Map.of("numeroCuenta", numero)).header("Authorization", "Bearer token")
                            .param("usuarioId", usuarioAjeno).param("clienteId", "999"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2));
        }
        when(repository.consultarCuentasPropias(eq(usuario.userId()), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        mvcReal.perform(postJson("/api/cuentas/mias/consulta", Map.of()).header("Authorization", "Bearer token")
                        .param("usuarioId", usuarioAjeno).param("clienteId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos").doesNotExist());
        verify(repository).consultarCuentaPropia(numero, usuario.userId());
        verify(repository).consultarSaldoPropio(numero, usuario.userId());
        verify(repository).consultarCuentasPropias(eq(usuario.userId()), any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void patchRequiereSesionYRolEjecutivoAntesDeAccederAlServicio() throws Exception {
String numero = "CTA-0123456789ABCDEF0123";
        mvc.perform(parcheCuenta(numero, "{\"activa\":false}")).andExpect(status().isUnauthorized());
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.CLIENTE);
        mvc.perform(parcheCuenta(numero, "{\"activa\":false}").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(cuentas);
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        mvc.perform(parcheCuenta(numero, "{\"activa\":false}").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(cuentas).actualizarEstado(numero, new com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest(false));
    }

    @Test
    void ejecutivoPuedeConsultarYCrearSoloConPayloadValido() throws Exception {
        when(sessions.rolVigente(any(), any())).thenReturn(RolUsuario.EJECUTIVO);
        mvc.perform(postJson("/api/clientes/consulta", Map.of()).header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").header("Authorization", "Bearer token")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").isString());
        mvc.perform(cuentaConsulta("CTA-0123456789ABCDEF0123").header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(bajaCuenta("CTA-0123456789ABCDEF0123").header("Authorization", "Bearer token")).andExpect(status().isOk());
        verify(clientes).consultar(argThat(request -> request.getPagina() == 0 && request.getTamanio() == 20));
        verify(cuentas).consultarPorNumeroCuenta("CTA-0123456789ABCDEF0123");
        verify(cuentas).desactivar("CTA-0123456789ABCDEF0123");
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
        mvc.perform(postJson("/api/clientes/consulta", Map.of()).header("Authorization", "Bearer token")).andExpect(status().isOk());
        mvc.perform(postJson("/api/clientes/consulta", Map.of()).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        verify(clientes).consultar(argThat(request -> request.getPagina() == 0 && request.getTamanio() == 20));
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
