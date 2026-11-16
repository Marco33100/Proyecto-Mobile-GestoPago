package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.JwtAuthenticationFilter;
import com.proyecto.servicios.config.LoginRateLimitProperties;
import com.proyecto.servicios.config.RestAuthenticationEntryPoint;
import com.proyecto.servicios.config.SecurityConfiguration;
import com.proyecto.servicios.config.WebSessionCookies;
import com.proyecto.servicios.config.WebSessionProperties;
import com.proyecto.servicios.entity.sf.PlataformaSesion;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.exception.auth.SesionActivaException;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.SesionActivaResponse;
import com.proyecto.servicios.model.auth.VerificarLoginRequest;
import com.proyecto.servicios.model.auth.UserProfileResponse;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebSessionSecurityTest {
    private static final String WEB_ORIGIN = "http://localhost:5173";
    private static final String LOGIN = "{\"email\":\"uno@example.com\",\"password\":\"Actual123!\"}";
    private static final String VERIFICAR = "{\"desafio\":\"desafio-firmado\",\"codigo\":\"123456\"}";

    private AnnotationConfigWebApplicationContext context;
    private AuthService auth;
    private com.proyecto.servicios.service.LoginSegundoFactorService loginService;
    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("app.database.enabled", "true")));
        context.register(SecurityConfiguration.class, TestConfiguration.class,
                LoginRateLimitProperties.class, LoginAttemptLimiter.class);
        context.refresh();

        auth = mock(AuthService.class);
        UserProfileResponse perfil = new UserProfileResponse(
                userId, "uno@example.com", "uno", "Marco Martinez", RolUsuario.CLIENTE);
        loginService = mock(com.proyecto.servicios.service.LoginSegundoFactorService.class);
        when(loginService.iniciar(any())).thenReturn(new com.proyecto.servicios.model.auth.LoginDesafioResponse(
                "desafio-firmado", true, Instant.now().plusSeconds(300), null));
        when(loginService.verificar(any(), any())).thenReturn(new LoginResponse(
                "jwt-emitido", "Bearer", Instant.now().plusSeconds(3600), perfil));
        when(auth.perfilActual(any())).thenReturn(perfil);

        JwtTokenService jwt = context.getBean(JwtTokenService.class);
        when(jwt.validate("token-valido")).thenReturn(
                new AuthenticatedUser(userId, UUID.randomUUID(), "uno@example.com", "uno"));
        when(jwt.validate("token-invalido")).thenThrow(new io.jsonwebtoken.MalformedJwtException("x"));
        when(context.getBean(ActiveSessionService.class).rolVigente(any(), any()))
                .thenReturn(RolUsuario.CLIENTE);

        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, loginService,
                        context.getBean(LoginAttemptLimiter.class), context.getBean(WebSessionCookies.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new ValidadorCampos(), new GlobalApiExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void loginWebEntregaJwtEnCookieHttpOnlyYNoEnElCuerpo() throws Exception {
        mvc.perform(post("/api/auth/login/verificar").header("X-Auth-Mode", "cookie")
                        .contentType("application/json").content(VERIFICAR))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("GP_SESSION=jwt-emitido"), containsString("HttpOnly"),
                        containsString("Secure"), containsString("SameSite=Lax"),
                        containsString("Path=/api"), containsString("Max-Age="))))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("datos.accessToken").doesNotExist())
                .andExpect(jsonPath("datos.user.id").value(userId.toString()));
        verify(loginService).verificar(any(), eq(PlataformaSesion.WEB));
    }

    @Test
    void loginMovilConservaElTokenEnElCuerpoSinCookies() throws Exception {
        mvc.perform(post("/api/auth/login/verificar").contentType("application/json").content(VERIFICAR))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("datos.accessToken").value("jwt-emitido"));
        verify(loginService).verificar(any(), eq(PlataformaSesion.MOVIL));
    }

    @Test
    void sesionAbiertaEnOtroDispositivoResponde409ConSuPlataformaYSinCookie() throws Exception {
        when(loginService.verificar(any(), any())).thenThrow(new SesionActivaException(
                new SesionActivaResponse(PlataformaSesion.MOVIL, Instant.parse("2026-11-15T10:00:00Z"))));
        mvc.perform(post("/api/auth/login/verificar").header("X-Auth-Mode", "cookie")
                        .contentType("application/json").content(VERIFICAR))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").value(containsString("app movil")))
                .andExpect(jsonPath("datos.plataforma").value("MOVIL"))
                .andExpect(jsonPath("datos.iniciadaEn").exists());
    }

    @Test
    void confirmarCierreSoloAceptaUnBooleanoReal() throws Exception {
        mvc.perform(post("/api/auth/login/verificar").contentType("application/json")
                        .content("{\"desafio\":\"d\",\"codigo\":\"123456\",\"cerrarSesionAnterior\":true}"))
                .andExpect(status().isOk());
        verify(loginService).verificar(argThat(VerificarLoginRequest::confirmaCerrarSesionAnterior), any());

        mvc.perform(post("/api/auth/login/verificar").contentType("application/json")
                        .content("{\"desafio\":\"d\",\"codigo\":\"123456\",\"cerrarSesionAnterior\":\"true\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sesionReemplazadaExplicaDondeSeInicioLaNueva() throws Exception {
        ActiveSessionService sesiones = context.getBean(ActiveSessionService.class);
        when(sesiones.rolVigente(any(), any())).thenReturn(null);
        when(sesiones.sesionVigente(userId)).thenReturn(Optional.of(new UserSessionEntity(userId, UUID.randomUUID(),
                Instant.now().plusSeconds(3600), Instant.now(), PlataformaSesion.WEB)));
        mvc.perform(get("/api/auth/sesion").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("mensaje").value(containsString("plataforma web")))
                .andExpect(jsonPath("datos.plataforma").value("WEB"));
    }

    @Test
    void sesionVencidaOCerradaConservaElMensajeGenerico() throws Exception {
        when(context.getBean(ActiveSessionService.class).rolVigente(any(), any())).thenReturn(null);
        mvc.perform(get("/api/auth/sesion").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("datos").doesNotExist());
    }

    @Test
    void pasoDeContrasenaNuncaEntregaSesionNiCookie() throws Exception {
        mvc.perform(post("/api/auth/login").header("X-Auth-Mode", "cookie")
                        .contentType("application/json").content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("datos.accessToken").doesNotExist())
                .andExpect(jsonPath("datos.desafio").value("desafio-firmado"))
                .andExpect(jsonPath("datos.segundoFactorConfigurado").value(true));
        verify(loginService, never()).verificar(any(), any());
    }

    @Test
    void verificacionFuncionaConCookieViejaYSinCsrf() throws Exception {
        // Un navegador puede traer una cookie de una sesion vencida: no debe bloquear el login.
        mvc.perform(post("/api/auth/login/verificar").header("X-Auth-Mode", "cookie")
                        .cookie(new Cookie("GP_SESSION", "token-invalido"))
                        .contentType("application/json").content(VERIFICAR))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("GP_SESSION=jwt-emitido"))));
    }

    @Test
    void verificacionRechazaCodigoMalFormadoSinLlamarAlServicio() throws Exception {
        mvc.perform(post("/api/auth/login/verificar").contentType("application/json")
                        .content("{\"desafio\":\"d\",\"codigo\":\"12ab\"}"))
                .andExpect(status().isBadRequest());
        verify(loginService, never()).verificar(any(), any());
    }

    @Test
    void cookieDeSesionAutenticaConsultas() throws Exception {
        mvc.perform(get("/api/auth/sesion").cookie(new Cookie("GP_SESSION", "token-valido")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.id").value(userId.toString()));
        mvc.perform(get("/api/auth/sesion").cookie(new Cookie("GP_SESSION", "token-invalido")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sesionReemplazadaInvalidaTambienLaCookie() throws Exception {
        when(context.getBean(ActiveSessionService.class).rolVigente(any(), any())).thenReturn(null);
        mvc.perform(get("/api/auth/sesion").cookie(new Cookie("GP_SESSION", "token-valido")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operacionConCookieSinTokenCsrfSeRechaza() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("GP_SESSION", "token-valido")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("GP_SESSION", "token-valido"))
                        .header("X-XSRF-TOKEN", "inventado"))
                .andExpect(status().isForbidden());
        verify(auth, never()).logout(any());
    }

    @Test
    void operacionConCookieYTokenCsrfSeAceptaYBorraLasCookies() throws Exception {
        Cookie session = new Cookie("GP_SESSION", "token-valido");
        MvcResult csrf = mvc.perform(get("/api/auth/csrf").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.headerName").value("X-XSRF-TOKEN"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("GP_XSRF");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isTrue();
        String token = com.jayway.jsonpath.JsonPath.read(
                csrf.getResponse().getContentAsString(), "$.datos.token");

        mvc.perform(post("/api/auth/logout").cookie(session, csrfCookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("GP_SESSION="), containsString("Max-Age=0")))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("GP_XSRF="), containsString("Max-Age=0")))));
        verify(auth).logout(argThat(principal -> principal.userId().equals(userId)));
    }

    @Test
    void peticionAutenticadaNoBorraLaCookieCsrfComoHariaElNavegador() throws Exception {
        Cookie session = new Cookie("GP_SESSION", "token-valido");
        MvcResult csrf = mvc.perform(get("/api/auth/csrf").cookie(session)).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("GP_XSRF");
        String token = com.jayway.jsonpath.JsonPath.read(
                csrf.getResponse().getContentAsString(), "$.datos.token");

        // Antes, cada peticion autenticada respondia GP_XSRF=; Max-Age=0 y el POST fallaba con 403.
        mvc.perform(get("/api/auth/sesion").cookie(session, csrfCookie))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/api/auth/logout").cookie(session, csrfCookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginWebRotaElTokenCsrfAnterior() throws Exception {
        mvc.perform(post("/api/auth/login/verificar").header("X-Auth-Mode", "cookie")
                        .cookie(new Cookie("GP_XSRF", "plantado-antes-del-login"))
                        .contentType("application/json").content(VERIFICAR))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("GP_XSRF="), containsString("Max-Age=0")))));
    }

    @Test
    void bearerNoRequiereCsrfAunqueExistaCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-valido")
                        .cookie(new Cookie("GP_SESSION", "token-valido")))
                .andExpect(status().isNoContent());
    }

    @Test
    void corsPermiteSoloLosOrigenesConfiguradosConCredenciales() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", WEB_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-auth-mode"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options("/api/auth/login").header("Origin", "https://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void sinOrigenesConfiguradosNoSeHabilitaCors() {
        var source = new SecurityConfiguration().corsConfigurationSource(new WebSessionProperties());
        var request = new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/auth/login");
        assertThat(source.getCorsConfiguration(request)).isNull();
    }

    @Test
    void variableDeOrigenesVaciaOSeparadaPorComasSeInterpretaComoLista() {
        assertThat(bind("").getAllowedOrigins()).isEmpty();
        assertThat(bind("https://web.example.com,http://localhost:[*]").getAllowedOrigins())
                .containsExactly("https://web.example.com", "http://localhost:[*]");
    }

    private static WebSessionProperties bind(String origins) {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                Map.of("app.web-session.allowed-origins", origins));
        return new org.springframework.boot.context.properties.bind.Binder(source)
                .bindOrCreate("app.web-session", WebSessionProperties.class);
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class TestConfiguration {
        @Bean WebSessionProperties webSessionProperties() {
            WebSessionProperties properties = new WebSessionProperties();
            properties.setAllowedOrigins(List.of("http://localhost:[*]"));
            return properties;
        }
        @Bean WebSessionCookies webSessionCookies(WebSessionProperties properties) {
            return new WebSessionCookies(properties);
        }
        @Bean JwtTokenService jwt() { return mock(JwtTokenService.class); }
        @Bean ActiveSessionService sessions() { return mock(ActiveSessionService.class); }
        @Bean RestAuthenticationEntryPoint entryPoint() {
            return new RestAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());
        }
        @Bean JwtAuthenticationFilter filter(JwtTokenService jwt, ActiveSessionService sessions,
                                             RestAuthenticationEntryPoint entryPoint, WebSessionCookies cookies) {
            return new JwtAuthenticationFilter(jwt, sessions, entryPoint, cookies);
        }
    }
}
