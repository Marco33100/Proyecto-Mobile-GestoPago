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
import static com.proyecto.servicios.controller.ApiTestRequests.*;
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
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler())
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
                .andExpect(jsonPath("codigo").value(2));
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
                .andExpect(jsonPath("codigo").value(2));
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
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("datos.correo").value(usuario.getEmail()))
                .andExpect(jsonPath("datos.passwordHash").doesNotExist());
    }

    @Test
    void consultaCompuestaNoPermiteSuplantarElPrincipalConParametrosDeUrl() throws Exception {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        mvc.perform(usuarioConsulta("MARCO MARTINEZ", "UNO@EXAMPLE.COM")
                        .param("usuarioId", UUID.randomUUID().toString()).param("correo", "otro@example.com")
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.id").value(usuario.getId().toString()));
        verify(usuarios).consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId());
        verifyNoMoreInteractions(usuarios);
        verifyNoInteractions(sesiones);
    }

    @Test
    void consultaExigeNombreYCorreoYRechazaElSelectorUuidAnterior() throws Exception {
        for (var datos : List.of(Map.of(), Map.of("nombre", "Marco Martinez"),
                Map.of("correo", "uno@example.com"), Map.of("nombre", " ", "correo", "uno@example.com"),
                Map.of("nombre", "Marco Martinez", "correo", ""),
                Map.of("nombre", "Marco Martinez", "correo", "correo-invalido"),
                Map.of("nombre", "A".repeat(204), "correo", "uno@example.com"),
                Map.of("id", usuario.getId().toString()),
                Map.of("nombre", "Marco Martinez", "correo", "uno@example.com", "usuarioId", usuario.getId().toString()))) {
            mvc.perform(postJson("/api/usuarios/consulta", datos).header("Authorization", "Bearer token-valido"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
        mvc.perform(postJson("/api/usuarios/consulta", Map.of()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString(
                        "nombre=El nombre es obligatorio y no puede contener solo espacios.")))
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString(
                        "correo=El correo electrónico es obligatorio.")));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void falloDePersistenciaEnConsultaDevuelve503SinDetallesInternos() throws Exception {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:password=secreto"));
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
        verifyNoInteractions(sesiones);
    }

    @Test
    void busquedaAdministrativaRequiereSesionYRolEjecutivo() throws Exception {
        mvc.perform(postJson("/api/usuarios/busqueda", Map.of())).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/usuarios/busqueda", Map.of()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void ejecutivoBuscaConFiltrosYRecibeUnaPaginaSegura() throws Exception {
        when(active.rolVigente(any(), any())).thenReturn(com.proyecto.servicios.entity.sf.RolUsuario.EJECUTIVO);
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserEntity>>any(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(usuario)));
        mvc.perform(postJson("/api/usuarios/busqueda", Map.of("nombre", "Marco", "correo", "uno@example.com",
                        "telefono", "5512345678", "activo", true, "pagina", 0, "tamanio", 10))
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos.contenido[0].nombre").value(usuario.getFullName()))
                .andExpect(jsonPath("datos.contenido[0].correo").value(usuario.getEmail()))
                .andExpect(jsonPath("datos.contenido[0].passwordHash").doesNotExist());
        verify(usuarios).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserEntity>>any(),
                argThat((org.springframework.data.domain.Pageable p) -> p.getPageNumber() == 0 && p.getPageSize() == 10));
        verifyNoMoreInteractions(usuarios);
        verifyNoInteractions(sesiones);
    }

    @Test
    void busquedaVaciaDevuelveCodigoCeroSinDatosNiFallback() throws Exception {
        when(active.rolVigente(any(), any())).thenReturn(com.proyecto.servicios.entity.sf.RolUsuario.EJECUTIVO);
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserEntity>>any(),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(postJson("/api/usuarios/busqueda", Map.of("nombre", "sincoincidencias"))
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("mensaje").value("No se encontraron datos"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        verify(usuarios).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserEntity>>any(),
                any(org.springframework.data.domain.Pageable.class));
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void filtrosInvalidosYCamposDesconocidosNoEjecutanConsultas() throws Exception {
        when(active.rolVigente(any(), any())).thenReturn(com.proyecto.servicios.entity.sf.RolUsuario.EJECUTIVO);
        for (var filtros : List.of(Map.of("nombre", " "), Map.of("nombre", "A".repeat(204)),
                Map.of("correo", "invalido"), Map.of("correo", ""), Map.of("telefono", "123"),
                Map.of("telefono", "551234567A"), Map.of("pagina", -1), Map.of("tamanio", 0),
                Map.of("tamanio", 101), Map.of("activo", "invalido"), Map.of("rol", "EJECUTIVO"))) {
            mvc.perform(postJson("/api/usuarios/busqueda", filtros).header("Authorization", "Bearer token-valido"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
        mvc.perform(post("/api/usuarios/busqueda").header("Authorization", "Bearer token-valido")
                        .contentType("application/json")).andExpect(status().isBadRequest());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void falloDeBusquedaDevuelve503SeguroEnLugarDeResultadosVacios() throws Exception {
        when(active.rolVigente(any(), any())).thenReturn(com.proyecto.servicios.entity.sf.RolUsuario.EJECUTIVO);
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<UserEntity>>any(),
                any(org.springframework.data.domain.Pageable.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:password=secreto"));
        mvc.perform(postJson("/api/usuarios/busqueda", Map.of()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value(2))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
    }

    @Test
    void datosDeOtroUsuarioNoPermitenConsultarNiCambiar() throws Exception {
        mvc.perform(usuarioConsulta("Otro Usuario", "otro@example.com").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2));
        mvc.perform(usuarioPassword("Otro Usuario", "otro@example.com", "{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}")
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        verify(usuarios).consultarPropioPorNombreYCorreo("otro usuario", "otro@example.com", usuario.getId());
        verify(usuarios).consultarPropioPorNombreYCorreoParaActualizar("otro usuario", "otro@example.com", usuario.getId());
        verifyNoMoreInteractions(usuarios);
        verifyNoInteractions(sesiones);
    }

    @Test
    void sinTokenNoSePuedeConsultarNiCambiar() throws Exception {
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail())).andExpect(status().isUnauthorized());
        mvc.perform(usuarioPassword(usuario.getFullName(), usuario.getEmail(), "{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void tokenInvalidoYSesionReemplazadaNoDanAcceso() throws Exception {
        JwtTokenService jwt = context.getBean(JwtTokenService.class);
        when(jwt.validate("token-invalido")).thenThrow(new io.jsonwebtoken.MalformedJwtException("invalido"));
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value(2));
        when(active.rolVigente(any(), any())).thenReturn(null);
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void cambioPropioDevuelve204YRevocaSesion() throws Exception {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        mvc.perform(usuarioPassword("MARCO MARTINEZ", "UNO@EXAMPLE.COM",
                        "{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}")
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(sesiones).deleteById(usuario.getId());
    }

    @Test
    void loginNoRevelaUsuarioInexistenteOInactivo() throws Exception {
        when(auth.login(any())).thenThrow(new UsuarioNoEncontradoException(), new UsuarioInactivoException());
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"uno@example.com\",\"password\":\"Actual123!\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").value("El correo o la contrasena son incorrectos"));
        }
    }

    @Test
    void tokenNoAutorizaSiFallaPostgresAunqueHayaCache() throws Exception {
        when(active.rolVigente(any(), any())).thenThrow(new AuthPersistenceException("fallo", null));
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(usuarios);
    }

    @Test
    void registroAntiguoNoPuedeCrearUsuariosNiCuentas() throws Exception {
        mvc.perform(post("/api/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(auth, usuarios, sesiones);
    }

    @Test
    void contrasenaDebilSeRechazaSinConsultarUsuario() throws Exception {
        mvc.perform(usuarioPassword(usuario.getFullName(), usuario.getEmail(),
                        "{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Debil123\"}")
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString("credenciales.nuevaContrasena")))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void loginConTokenAnteriorIgnoraEseTokenYFalloDeTransaccionDevuelve503() throws Exception {
        when(auth.login(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("fallo"));
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer token-anterior")
                .contentType("application/json").content("{\"email\":\"uno@example.com\",\"password\":\"Actual123!\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value(2));
    }

    @Test
    void combinacionInexistenteYCorreoMalformadoDevuelvenErroresClaros() throws Exception {
        mvc.perform(usuarioConsulta(usuario.getFullName(), usuario.getEmail()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value(2));
        mvc.perform(usuarioConsulta(usuario.getFullName(), "incorrecto").header("Authorization", "Bearer token-valido"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
    }

    @Test
    void cambioExigeNombreYCorreoYRechazaElSelectorIdAnterior() throws Exception {
        for (var selector : List.<Map<String, String>>of(Map.of(), Map.of("nombre", usuario.getFullName()),
                Map.of("correo", usuario.getEmail()), Map.of("nombre", " ", "correo", usuario.getEmail()),
                Map.of("nombre", usuario.getFullName(), "correo", ""),
                Map.of("nombre", usuario.getFullName(), "correo", "incorrecto"),
                Map.of("nombre", "A".repeat(204), "correo", usuario.getEmail()),
                Map.of("id", usuario.getId().toString()),
                Map.of("nombre", usuario.getFullName(), "correo", usuario.getEmail(), "id", usuario.getId().toString()))) {
            var cambio = new java.util.HashMap<String, Object>();
            cambio.putAll(selector);
            cambio.put("credenciales", Map.of("contrasenaActual", "Actual123!", "nuevaContrasena", "Nueva456!"));
            var result = mvc.perform(json(org.springframework.http.HttpMethod.PUT, "/api/usuarios/password", cambio)
                            .header("Authorization", "Bearer token-valido"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
            if (selector.isEmpty()) result
                    .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString(
                            "nombre=El nombre es obligatorio y no puede contener solo espacios.")))
                    .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString(
                            "correo=El correo electrónico es obligatorio.")));
        }
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void cambiarContrasenaNoAceptaCamposProtegidosNiCredencialesAusentes() throws Exception {
        var base = Map.of("nombre", usuario.getFullName(), "correo", usuario.getEmail());
        for (var adicional : List.<Map<String, Object>>of(Map.of(), Map.of("credenciales", Map.of()),
                Map.of("credenciales", Map.of("contrasenaActual", "Actual123!", "nuevaContrasena", "Nueva456!",
                        "rol", "EJECUTIVO")))) {
            var cambio = new java.util.HashMap<String, Object>();
            cambio.putAll(base);
            cambio.putAll(adicional);
            mvc.perform(json(org.springframework.http.HttpMethod.PUT, "/api/usuarios/password", cambio)
                            .header("Authorization", "Bearer token-valido"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        }
        verifyNoInteractions(usuarios, sesiones);
    }

    @Test
    void falloEnCambioDeContrasenaDevuelve503SinDetallesInternos() throws Exception {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("jdbc:password=secreto"));
        mvc.perform(usuarioPassword(usuario.getFullName(), usuario.getEmail(),
                        "{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}")
                        .header("Authorization", "Bearer token-valido"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value(2))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
        verifyNoInteractions(sesiones);
    }

    @Test
    void rutasAntiguasDeUsuarioNoPermitenConsultarNiCambiarContrasena() throws Exception {
        mvc.perform(get("/api/usuarios/" + usuario.getId()).header("Authorization", "Bearer token-valido"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/usuarios/" + usuario.getId() + "/password")
                        .header("Authorization", "Bearer token-valido").contentType("application/json")
                        .content("{\"contrasenaActual\":\"Actual123!\",\"nuevaContrasena\":\"Nueva456!\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(usuarios, sesiones);
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
