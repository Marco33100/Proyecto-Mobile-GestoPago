package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.*;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.Impl.ActiveSessionService;
import com.proyecto.servicios.service.Impl.UsuarioServiceImpl;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsuarioAuthSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private UserRepository usuarios;
    private UserSessionRepository sesiones;
    private AuthService auth;
    private ActiveSessionService active;
    private UserEntity usuario;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("app.database.enabled", "true")));
        context.register(SecurityConfiguration.class, SecurityTestConfiguration.class,
                LoginRateLimitProperties.class, com.proyecto.servicios.service.Impl.LoginAttemptLimiter.class);
        context.refresh();
        usuarios = mock(UserRepository.class);
        sesiones = mock(UserSessionRepository.class);
        auth = mock(AuthService.class);
        active = context.getBean(ActiveSessionService.class);
        var encoder = new BCryptPasswordEncoder(4);
        usuario = new UserEntity(UUID.randomUUID(), "uno@example.com", "uno", encoder.encode("Actual123!"),
                "Marco Martinez", true, Instant.now());
        JwtTokenService jwt = context.getBean(JwtTokenService.class);
        when(jwt.validate("token-valido")).thenReturn(new AuthenticatedUser(
                usuario.getId(), UUID.randomUUID(), usuario.getEmail(), usuario.getIdentifier()));
        when(active.rolVigente(any(), any())).thenReturn(com.proyecto.servicios.entity.sf.RolUsuario.CLIENTE);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth,
                context.getBean(com.proyecto.servicios.service.Impl.LoginAttemptLimiter.class)), new UsuarioController(
                new UsuarioServiceImpl(usuarios, sesiones, encoder, mock(ApplicationEventPublisher.class))))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AuthExceptionHandler())
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void tearDown() { context.close(); }

    @Test
    void excesoDeSolicitudesIpDevuelve429InclusoConPayloadInvalido() throws Exception {
        context.getBean(LoginRateLimitProperties.class).setAddressAttempts(2);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For", "192.0.2.123")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("code").value("AUTH_TOO_MANY_ATTEMPTS"));
        verifyNoInteractions(auth);
    }

    @Test
    void intentosCuentaSeCompartenEntreIpsYNoRevelanSiExisteUsuario() throws Exception {
        context.getBean(LoginRateLimitProperties.class).setAccountAttempts(2);
        when(auth.login(any())).thenThrow(new InvalidCredentialsException());
        String payload = "{\"email\":\"uno@example.com\",\"password\":\"Incorrecta123!\"}";
        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/api/auth/login").with(request -> {
                        request.setRemoteAddr(UUID.randomUUID().toString()); return request;
                    }).contentType("application/json").content(payload))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("192.0.2.250"); return request;
                }).contentType("application/json").content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("code").value("AUTH_TOO_MANY_ATTEMPTS"));
        verify(auth, times(2)).login(any());
    }

    @Test
    void logoutRequiereSesionYUsaElPrincipalAutenticado() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNoContent());
        verify(auth).logout(argThat(principal -> principal.userId().equals(usuario.getId())));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void autenticacionYUsuariosSinPrefijoApiYaNoEstanExpuestos() throws Exception {
        for (String ruta : List.of("/auth/login", "/auth/logout")) {
            mvc.perform(post(ruta).header("Authorization", "Bearer token-valido")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/usuarios/" + usuario.getId() + "/password")
                .header("Authorization", "Bearer token-valido")
                .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(auth, usuarios, sesiones);
    }

    @Test
    void logoutConSesionReemplazadaNoLlamaAlServicio() throws Exception {
        when(active.rolVigente(any(), any())).thenReturn(null);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test
    void falloPostgresAlCerrarSesionDevuelve503SinDetallesSensibles() throws Exception {
        doThrow(new AuthPersistenceException("offline", new IllegalStateException("secreto")))
                .when(auth).logout(any());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
    }

    @Test
    void consultaPropiaNoExponeHash() throws Exception {
        when(usuarios.findUserById(usuario.getId())).thenReturn(usuario);
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isOk()).andExpect(jsonPath("correo").value(usuario.getEmail()))
                .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    @Test
    void idDeOtroUsuarioNoPermiteConsultarNiCambiar() throws Exception {
        String ruta = "/api/usuarios/" + UUID.randomUUID();
        mvc.perform(get(ruta).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("AUTH_FORBIDDEN"));
        mvc.perform(put(ruta + "/password").header("Authorization", "Bearer token-valido")
                .contentType("application/json").content("{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void sinTokenNoSePuedeConsultarNiCambiar() throws Exception {
        String ruta = "/api/usuarios/" + usuario.getId();
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mvc.perform(put(ruta + "/password")).andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void tokenInvalidoYSesionReemplazadaNoDanAcceso() throws Exception {
        JwtTokenService jwt = context.getBean(JwtTokenService.class);
        when(jwt.validate("token-invalido")).thenThrow(new io.jsonwebtoken.MalformedJwtException("invalido"));
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("AUTH_INVALID_SESSION"));
        when(active.rolVigente(any(), any())).thenReturn(null);
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void cambioPropioDevuelve204YRevocaSesion() throws Exception {
        when(usuarios.findByIdForUpdate(usuario.getId())).thenReturn(usuario);
        mvc.perform(put("/api/usuarios/" + usuario.getId() + "/password")
                .header("Authorization", "Bearer token-valido").contentType("application/json")
                .content("{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}"))
                .andExpect(status().isNoContent());
        verify(sesiones).deleteById(usuario.getId());
    }

    @Test
    void loginNoRevelaUsuarioInexistenteOInactivo() throws Exception {
        when(auth.login(any())).thenThrow(new UsuarioNoEncontradoException(), new UsuarioInactivoException());
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"uno@example.com\",\"password\":\"Actual123!\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("AUTH_INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("message").value("El correo o la contrasena son incorrectos"));
        }
    }

    @Test
    void tokenNoAutorizaSiFallaPostgresAunqueHayaCache() throws Exception {
        when(active.rolVigente(any(), any())).thenThrow(new AuthPersistenceException("fallo", null));
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTH_DATABASE_ERROR"));
        verifyNoInteractions(usuarios);
    }

    @Test
    void registroAntiguoNoPuedeCrearUsuariosNiCuentas() throws Exception {
        mvc.perform(post("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("AUTH_FORBIDDEN"));
        verifyNoInteractions(auth, usuarios, sesiones);
    }

    @Test
    void contrasenaDebilSeRechazaSinConsultarUsuario() throws Exception {
        mvc.perform(put("/api/usuarios/" + usuario.getId() + "/password")
                .header("Authorization", "Bearer token-valido").contentType("application/json")
                .content("{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Debil123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("AUTH_INVALID_PAYLOAD"))
                .andExpect(jsonPath("fieldErrors.nuevaContrasena").exists());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void loginConTokenAnteriorIgnoraEseTokenYFalloDeTransaccionDevuelve503() throws Exception {
        when(auth.login(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("fallo"));
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer token-anterior")
                .contentType("application/json").content("{\"email\":\"uno@example.com\",\"password\":\"Actual123!\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("AUTH_DATABASE_ERROR"));
    }

    @Test
    void usuarioNoEncontradoYUuidMalformadoDevuelvenErroresClaros() throws Exception {
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("AUTH_USER_NOT_FOUND"));
        mvc.perform(get("/api/usuarios/incorrecto").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("AUTH_INVALID_PARAMETER"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class SecurityTestConfiguration {
        @Bean JwtTokenService jwt() { return mock(JwtTokenService.class); }
        @Bean ActiveSessionService sessions() { return mock(ActiveSessionService.class); }
        @Bean RestAuthenticationEntryPoint entryPoint() {
            return new RestAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());
        }
        @Bean JwtAuthenticationFilter filter(JwtTokenService jwt, ActiveSessionService sessions,
                                            RestAuthenticationEntryPoint entryPoint) {
            return new JwtAuthenticationFilter(jwt, sessions, entryPoint);
        }
    }
}
