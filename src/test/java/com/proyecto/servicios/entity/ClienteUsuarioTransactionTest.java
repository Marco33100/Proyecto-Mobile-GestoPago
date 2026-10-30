package com.proyecto.servicios.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.AuthConfiguration;
import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.config.ExecutiveBootstrapProperties;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.exception.cliente.CuentaPersistenceException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.ContrasenaInvalidaException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.UsuarioConsultaRequest;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.auth.ModificarContrasenaRequest;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.UsuarioService;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.Impl.*;
import org.hibernate.SessionFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class ClienteUsuarioTransactionTest {
    private SessionFactory factory;
    private JpaTransactionManager transactionManager;
    private ClienteRepository clientes;
    private DomicilioRepository domicilios;
    private CuentaRepository cuentas;
    private UserRepository usuarios;
    private UserSessionRepository sesiones;
    private PasswordEncoder encoder;
    private String postgresUrl;
    private String postgresSchema;
    private String postgresUser;
    private String postgresPassword;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ClienteEntity.class).addAnnotatedClass(DomicilioEntity.class)
                .addAnnotatedClass(CuentaEntity.class).addAnnotatedClass(UserEntity.class)
                .addAnnotatedClass(UserSessionEntity.class);
        postgresUrl = System.getenv("ONBOARDING_TEST_JDBC_URL");
        if (postgresUrl == null || postgresUrl.isBlank()) {
            configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
            configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID());
            configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        } else {
            // Schema aislado por prueba; nunca limpiar ni migrar el schema de la aplicacion.
            postgresSchema = "sf_test_" + UUID.randomUUID().toString().replace("-", "");
            postgresUser = System.getenv().getOrDefault("ONBOARDING_TEST_DB_USER", "onboarding_test");
            postgresPassword = System.getenv().getOrDefault("ONBOARDING_TEST_DB_PASSWORD", "");
            Flyway.configure().dataSource(postgresUrl, postgresUser, postgresPassword)
                    .schemas(postgresSchema).defaultSchema(postgresSchema)
                    .locations("classpath:db/initial", "classpath:db/payments").baselineOnMigrate(false).load().migrate();
            configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
            configuration.setProperty("hibernate.connection.url", postgresUrl);
            configuration.setProperty("hibernate.connection.username", postgresUser);
            configuration.setProperty("hibernate.connection.password", postgresPassword);
            configuration.setProperty("hibernate.default_schema", postgresSchema);
            configuration.setProperty("hibernate.connection.init_sql", "SET search_path TO " + postgresSchema);
            configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        }
        factory = configuration.buildSessionFactory();
        transactionManager = new JpaTransactionManager(factory);
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(
                SharedEntityManagerCreator.createSharedEntityManager(factory));
        clientes = repositories.getRepository(ClienteRepository.class);
        domicilios = repositories.getRepository(DomicilioRepository.class);
        cuentas = repositories.getRepository(CuentaRepository.class);
        usuarios = repositories.getRepository(UserRepository.class);
        sesiones = repositories.getRepository(UserSessionRepository.class);
        encoder = new AuthConfiguration().passwordEncoder();
    }

    @AfterEach
    void tearDown() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if (factory != null) factory.close();
        if (postgresSchema != null) {
            // Nombre generado aqui, no recibido del usuario; solo borrar nuestro schema temporal.
            if (!postgresSchema.matches("sf_test_[a-f0-9]{32}")) throw new IllegalStateException("Schema de prueba invalido");
            try (var connection = DriverManager.getConnection(postgresUrl, postgresUser, postgresPassword);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + postgresSchema + " CASCADE");
            }
        }
    }

    @Test
    void registroCreaUsuarioConBcryptYPermiteLoginExistente() throws Exception {
        var response = registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        assertThat(usuario.getCliente().getId()).isEqualTo(response.id());
        assertThat(usuario.getIdentifier()).isEqualTo(usuario.getEmail());
        assertThat(usuario.isEnabled()).isTrue();
        assertThat(usuario.getRol()).isEqualTo(RolUsuario.CLIENTE);
        assertThat(usuario.getPasswordHash()).startsWith("$2a$12$");
        assertThat(encoder.matches(request().contrasena(), usuario.getPasswordHash())).isTrue();
        assertThat(cuentas.count()).isEqualTo(1);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
                .doesNotContain("contrasena", "passwordHash", request().contrasena());

        JwtTokenService jwt = mock(JwtTokenService.class);
        when(jwt.generate(any(), any())).thenReturn(new JwtTokenService.TokenResult(
                "token-de-prueba", Instant.now().plusSeconds(300)));
        AuthService auth = proxy(new AuthServiceImpl(usuarios, encoder,
                jwt, mock(ActiveSessionService.class)), AuthService.class);
        assertThat(auth.login(new LoginRequest("marco@example.com", request().contrasena())).accessToken())
                .isEqualTo("token-de-prueba");
        assertThatThrownBy(() -> auth.login(new LoginRequest("marco@example.com", "Incorrecta1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void primerEjecutivoNoTieneCuentaBancariaYBootstrapEsIdempotente() {
        var properties = new ExecutiveBootstrapProperties();
        properties.setEnabled(true);
        properties.setEmail("ejecutivo@example.com");
        properties.setPassword("Segura123!");
        var bootstrap = proxy(new ExecutiveBootstrapService(usuarios, encoder, properties),
                ExecutiveBootstrapService.class);
        bootstrap.crearSiSeSolicita();
        UserEntity ejecutivo = usuarios.findByEmail("ejecutivo@example.com").orElseThrow();
        assertThat(ejecutivo.getCliente()).isNull();
        assertThat(ejecutivo.getRol()).isEqualTo(RolUsuario.EJECUTIVO);
        assertThat(clientes.count()).isZero();
        assertThat(cuentas.count()).isZero();
        String hashOriginal = ejecutivo.getPasswordHash();
        properties.setPassword("OtraSegura456!");
        bootstrap.crearSiSeSolicita();
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(usuarios.findById(ejecutivo.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashOriginal);
        UUID sesion = crearSesion(ejecutivo.getId());
        RolUsuario rolVigente = new TransactionTemplate(transactionManager).execute(status ->
                sesiones.encontrarRolVigente(ejecutivo.getId(), sesion, Instant.now()));
        assertThat(rolVigente).isEqualTo(RolUsuario.EJECUTIVO);
        registro(usuarios).registrar(request());
        assertThat(usuarios.count()).isEqualTo(2);
        assertThat(cuentas.count()).isEqualTo(1);
        assertThat(usuarios.findByEmail("marco@example.com").orElseThrow().getRol()).isEqualTo(RolUsuario.CLIENTE);
    }

    @Test
    void falloDeBootstrapHaceRollbackSinUsuarioParcial() {
        var properties = new ExecutiveBootstrapProperties();
        properties.setEnabled(true);
        properties.setEmail("ejecutivo@example.com");
        properties.setPassword("Segura123!");
        UserRepository falla = mock(UserRepository.class, delegatesTo(usuarios));
        doAnswer(invocation -> {
            usuarios.saveAndFlush(invocation.getArgument(0));
            throw new DataAccessResourceFailureException("fallo posterior al insert");
        }).when(falla).saveAndFlush(any());
        var bootstrap = proxy(new ExecutiveBootstrapService(falla, encoder, properties),
                ExecutiveBootstrapService.class);
        assertThatThrownBy(bootstrap::crearSiSeSolicita).isInstanceOf(IllegalStateException.class);
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void fallaUsuarioYHaceRollbackDeClienteDomicilioCuentaYUsuario() {
        UserRepository fallaAlGuardar = mock(UserRepository.class, delegatesTo(usuarios));
        doAnswer(invocation -> {
            usuarios.saveAndFlush(invocation.getArgument(0));
            throw new DataAccessResourceFailureException("fallo posterior al insert del usuario");
        }).when(fallaAlGuardar).saveAndFlush(any(UserEntity.class));

        assertThatThrownBy(() -> registro(fallaAlGuardar).registrar(request()))
                .isInstanceOf(ClientePersistenceException.class);
        assertThat(clientes.count()).isZero();
        assertThat(domicilios.count()).isZero();
        assertThat(cuentas.count()).isZero();
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void baseDeDatosImpideDosUsuariosParaUnCliente() {
        var response = registro(usuarios).registrar(request());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            ClienteEntity cliente = clientes.findClienteById(response.id());
            return usuarios.saveAndFlush(new UserEntity(UUID.randomUUID(), "otro@example.com",
                    encoder.encode("Segura123!"), "Otro usuario", cliente, Instant.now()));
        })).isInstanceOf(RuntimeException.class);
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void cambioDeContrasenaRevocaSesionPersistidaYGuardaNuevoHash() {
        registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();

        gestionUsuarios(sesiones).cambiarContrasena(new ModificarContrasenaRequest(usuario.getFullName(), usuario.getEmail(),
                new CambiarContrasenaRequest(request().contrasena(), "Nueva456!")), usuario.getId());

        UserEntity actualizado = usuarios.findByEmail(usuario.getEmail()).orElseThrow();
        assertThat(encoder.matches("Nueva456!", actualizado.getPasswordHash())).isTrue();
        assertThat(encoder.matches(request().contrasena(), actualizado.getPasswordHash())).isFalse();
        assertThat(actualizado.getUpdatedAt()).isAfterOrEqualTo(actualizado.getCreatedAt());
        assertThat(sesionValida(usuario.getId(), sesion)).isFalse();
    }

    @Test
    void falloAlRevocarHaceRollbackDelCambioDeHash() {
        registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        UserSessionRepository falla = mock(UserSessionRepository.class, delegatesTo(sesiones));
        doAnswer(invocation -> {
            sesiones.deleteById(invocation.getArgument(0));
            sesiones.flush();
            throw new DataAccessResourceFailureException("fallo despues del delete");
        }).when(falla).deleteById(usuario.getId());

        assertThatThrownBy(() -> gestionUsuarios(falla).cambiarContrasena(new ModificarContrasenaRequest(usuario.getFullName(), usuario.getEmail(),
                new CambiarContrasenaRequest(request().contrasena(), "Nueva456!")), usuario.getId()))
                .isInstanceOf(AuthPersistenceException.class);
        assertThat(usuarios.findByEmail(usuario.getEmail()).orElseThrow().getPasswordHash())
                .isEqualTo(usuario.getPasswordHash());
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();
    }

    @Test
    void contrasenaActualIncorrectaNoModificaHashNiSesion() {
        registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        assertThatThrownBy(() -> gestionUsuarios(sesiones).cambiarContrasena(new ModificarContrasenaRequest(usuario.getFullName(), usuario.getEmail(),
                new CambiarContrasenaRequest("Incorrecta123!", "Nueva456!")), usuario.getId()))
                .isInstanceOf(ContrasenaInvalidaException.class);
        assertThat(usuarios.findByEmail(usuario.getEmail()).orElseThrow().getPasswordHash())
                .isEqualTo(usuario.getPasswordHash());
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();
    }

    @Test
    void cuentaInactivaConservaSesionLoginYPerfilDelUsuarioActivo() {
        var cliente = registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        var servicioCuentas = proxy(new CuentaServiceImpl(cuentas, new CuentaProperties(),
                org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class)), CuentaService.class);
        var cuenta = cuentas.findAllByClienteId(cliente.id()).get(0);
        var response = servicioCuentas.desactivar(cuenta.getNumeroCuenta());
        assertThat(response.activa()).isFalse();
        assertThat(response.saldo()).isEqualByComparingTo(cuenta.getSaldo());
        assertThat(clientes.findClienteById(cliente.id()).isActivo()).isTrue();
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();
        assertThat(gestionUsuarios(sesiones).consultar(new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), usuario.getId())).isNotNull();
        JwtTokenService jwt = mock(JwtTokenService.class);
        when(jwt.generate(any(), any())).thenReturn(new JwtTokenService.TokenResult(
                "token-de-prueba", Instant.now().plusSeconds(300)));
        AuthService auth = proxy(new AuthServiceImpl(usuarios, encoder, jwt,
                mock(ActiveSessionService.class)), AuthService.class);
        assertThat(auth.login(new LoginRequest(usuario.getEmail(), request().contrasena())).accessToken())
                .isEqualTo("token-de-prueba");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        new com.proyecto.servicios.model.auth.AuthenticatedUser(usuario.getId(), sesion,
                                usuario.getEmail(), usuario.getIdentifier()), null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"))));
        assertThat(servicioCuentas.consultarPorNumeroCuenta(cuenta.getNumeroCuenta()).activa()).isFalse();
    }

    @Test
    void patchPersistidoSoloCambiaEstadoYRepetirloNoIncrementaVersion() {
        var cliente = registro(usuarios).registrar(request());
        var cuenta = cuentas.findAllByClienteId(cliente.id()).get(0);
        var servicio = proxy(new CuentaServiceImpl(cuentas, new CuentaProperties(),
                org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class)), CuentaService.class);
        servicio.actualizarEstado(cuenta.getNumeroCuenta(), new ActualizarEstadoCuentaRequest(false));
        var despues = cuentas.findByNumeroCuenta(cuenta.getNumeroCuenta());
        assertThat(despues.isActiva()).isFalse();
        assertThat(despues.getId()).isEqualTo(cuenta.getId());
        assertThat(despues.getSaldo()).isEqualByComparingTo(cuenta.getSaldo());
        assertThat(despues.getCliente().getId()).isEqualTo(cliente.id());
        assertThat(despues.getFechaCreacion()).isEqualTo(cuenta.getFechaCreacion());
        assertThat(despues.getVersion()).isEqualTo(cuenta.getVersion() + 1);
        servicio.actualizarEstado(cuenta.getNumeroCuenta(), new ActualizarEstadoCuentaRequest(false));
        var repetida = cuentas.findByNumeroCuenta(cuenta.getNumeroCuenta());
        assertThat(repetida.getVersion()).isEqualTo(despues.getVersion());
        assertThat(repetida.getFechaActualizacion()).isEqualTo(despues.getFechaActualizacion());
        assertThat(clientes.findClienteById(cliente.id()).isActivo()).isTrue();
    }

    @Test
    void falloAlGuardarPatchRevierteEstadoFechaVersionYConservaSaldo() {
        var cliente = registro(usuarios).registrar(request());
        var cuenta = cuentas.findAllByClienteId(cliente.id()).get(0);
        CuentaRepository repositorioFallido = mock(CuentaRepository.class, delegatesTo(cuentas));
        doThrow(new DataAccessResourceFailureException("error interno simulado"))
                .when(repositorioFallido).saveAndFlush(any(CuentaEntity.class));
        var servicio = proxy(new CuentaServiceImpl(repositorioFallido, new CuentaProperties(),
                org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class)), CuentaService.class);
        assertThatThrownBy(() -> servicio.actualizarEstado(cuenta.getNumeroCuenta(),
                new ActualizarEstadoCuentaRequest(false))).isInstanceOf(CuentaPersistenceException.class);
        var despues = cuentas.findByNumeroCuenta(cuenta.getNumeroCuenta());
        assertThat(despues.isActiva()).isTrue();
        assertThat(despues.getSaldo()).isEqualByComparingTo(cuenta.getSaldo());
        assertThat(despues.getFechaActualizacion()).isEqualTo(cuenta.getFechaActualizacion());
        assertThat(despues.getVersion()).isEqualTo(cuenta.getVersion());
    }

    @Test
    void sesionRechazaClienteDadoDeBaja() {
        var cliente = registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        assertThat(sesionValida(usuario.getId(), UUID.randomUUID())).isFalse();
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            cuentas.desactivarTodasPorCliente(cliente.id(), Instant.now());
            clientes.findClienteById(cliente.id()).desactivar(Instant.now());
            clientes.flush();
        });
        assertThat(sesionValida(usuario.getId(), sesion)).isFalse();
        AuthService auth = proxy(new AuthServiceImpl(usuarios, encoder, mock(JwtTokenService.class),
                mock(ActiveSessionService.class)), AuthService.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest(usuario.getEmail(), request().contrasena())))
                .isInstanceOf(com.proyecto.servicios.exception.auth.UsuarioInactivoException.class);
    }

    @Test
    void sesionRechazaUsuarioInactivoAunqueClienteSigaActivo() {
        registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID sesion = crearSesion(usuario.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            usuarios.findById(usuario.getId()).orElseThrow().desactivar();
            usuarios.flush();
        });
        assertThat(sesionValida(usuario.getId(), sesion)).isFalse();
    }

    @Test
    void reemplazaSesionYRechazaLaAnteriorYLaExpirada() {
        registro(usuarios).registrar(request());
        UserEntity usuario = usuarios.findByEmail("marco@example.com").orElseThrow();
        UUID anterior = crearSesion(usuario.getId());
        UUID nueva = crearSesion(usuario.getId());
        assertThat(sesionValida(usuario.getId(), anterior)).isFalse();
        assertThat(sesionValida(usuario.getId(), nueva)).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                sesiones.saveAndFlush(new UserSessionEntity(usuario.getId(), nueva,
                        Instant.now().minusSeconds(1), Instant.now())));
        assertThat(sesionValida(usuario.getId(), nueva)).isFalse();
    }

    @Test
    void logoutAntiguoNoBorraSesionNuevaYLogoutVigenteLaRevoca() {
        registro(usuarios).registrar(request());
        UUID user = usuarios.findByEmail("marco@example.com").orElseThrow().getId();
        UUID anterior = crearSesion(user);
        UUID nueva = crearSesion(user);
        var publisher = mock(ApplicationEventPublisher.class);
        var active = proxy(new ActiveSessionService(sesiones, mock(AuthCacheStore.class), publisher),
                ActiveSessionService.class);
        active.revokeSession(user, anterior);
        assertThat(sesionValida(user, nueva)).isTrue();
        verifyNoInteractions(publisher);
        active.revokeSession(user, nueva);
        assertThat(sesionValida(user, nueva)).isFalse();
        verify(publisher).publishEvent(new com.proyecto.servicios.model.auth.SesionRevocadaEvent(user, nueva));
    }

    @Test
    void redisSeInvalidaSoloDespuesDelCommitYNoTrasRollback() {
        registro(usuarios).registrar(request());
        UUID user = usuarios.findByEmail("marco@example.com").orElseThrow().getId();
        UUID session = crearSesion(user);
        AuthCacheStore cache = mock(AuthCacheStore.class);
        try (var events = new AnnotationConfigApplicationContext()) {
            events.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", java.util.Map.of("app.database.enabled", "true")));
            events.registerBean(TransactionalEventListenerFactory.class);
            events.registerBean(UsuarioClienteCacheListener.class, () -> new UsuarioClienteCacheListener(cache));
            events.refresh();
            var active = proxy(new ActiveSessionService(sesiones, cache, events), ActiveSessionService.class);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                active.revokeSession(user, session);
                verifyNoInteractions(cache);
                status.setRollbackOnly();
            });
            assertThat(sesionValida(user, session)).isTrue();
            verifyNoInteractions(cache);
            active.revokeSession(user, session);
            assertThat(sesionValida(user, session)).isFalse();
            verify(cache).invalidateSessionIfMatches(user, session);
        }
    }

    @Test
    void existenciaConsultaCorreoNormalizadoOIdentificadorSinCargarUsuario() {
        UserEntity historico = new UserEntity(UUID.randomUUID(), "LEGADO@example.com", "alias-legado",
                encoder.encode("Segura123!"), "Usuario de prueba", true, Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                usuarios.saveAndFlush(historico));

        assertThat(usuarios.existsByEmailOrIdentifier("legado@example.com")).isTrue();
        assertThat(usuarios.existsByEmailOrIdentifier("alias-legado")).isTrue();
        assertThat(usuarios.existsByEmailOrIdentifier("ausente@example.com")).isFalse();
        assertThat(usuarios.findByEmail("legado@example.com")).isPresent();
        assertThat(usuarios.findByEmail("ausente@example.com")).isEmpty();

        var cliente = registro(usuarios).registrar(request());
        assertThat(clientes.existsByCorreoAndIdNot("marco@example.com", cliente.id())).isFalse();
        assertThat(clientes.existsByCorreoAndIdNot("marco@example.com", cliente.id() + 1)).isTrue();
        assertThat(clientes.existsByCorreoAndIdNot("ausente@example.com", cliente.id())).isFalse();
    }

    @Test
    void consultaPorNombreYCorreoUsaAmbosCriteriosExactosYElUsuarioAutenticadoSinEscribir() {
        UserEntity primero = new UserEntity(UUID.randomUUID(), "uno@example.com", "uno@example.com",
                encoder.encode("Segura123!"), "Marco Martinez", true, Instant.now());
        UserEntity homonimo = new UserEntity(UUID.randomUUID(), "dos@example.com", "dos@example.com",
                encoder.encode("Segura123!"), "Marco Martinez", true, Instant.now());
        UserEntity tercero = new UserEntity(UUID.randomUUID(), "tres@example.com", "tres@example.com",
                encoder.encode("Segura123!"), "Maria Perez", true, Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                usuarios.saveAllAndFlush(java.util.List.of(primero, homonimo, tercero)));
        var servicio = gestionUsuarios(sesiones);
        factory.getStatistics().setStatisticsEnabled(true);
        factory.getStatistics().clear();

        var respuesta = servicio.consultar(new UsuarioConsultaRequest("MARCO MARTINEZ", "UNO@EXAMPLE.COM"), primero.getId());
        assertThat(respuesta.id()).isEqualTo(primero.getId());
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(1);
        assertThat(servicio.consultar(new UsuarioConsultaRequest("Marco Martinez", "dos@example.com"), homonimo.getId()).id())
                .isEqualTo(homonimo.getId());
        for (var selector : java.util.List.of(new UsuarioConsultaRequest("Marco", "uno@example.com"),
                new UsuarioConsultaRequest("Maria Perez", "uno@example.com"),
                new UsuarioConsultaRequest("Marco Martinez", "tres@example.com"),
                new UsuarioConsultaRequest("Marco Martinez", "dos@example.com"),
                new UsuarioConsultaRequest("Marco%", "uno@example.com"),
                new UsuarioConsultaRequest("' OR 1=1 --", "uno@example.com"))) {
            assertThatThrownBy(() -> servicio.consultar(selector, primero.getId()))
                    .isInstanceOf(com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException.class);
        }
        assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
        assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
        assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();
    }

    @Test
    void busquedaDinamicaCombinaFiltrosEnSqlYPaginaSinNMasUnoNiEscrituras() {
        Instant fecha = Instant.parse("2026-01-01T00:00:00Z");
        var primero = clienteDeBusqueda("Juan", "uno@example.com", "5511111111", 1, fecha);
        var segundo = clienteDeBusqueda("Juana", "dos@example.com", "5522222222", 2, fecha);
        var tercero = clienteDeBusqueda("Maria", "tres@example.com", "5533333333", 3, fecha);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            clientes.saveAllAndFlush(java.util.List.of(primero, segundo, tercero));
            var uno = new UserEntity(UUID.randomUUID(), primero.getCorreo(), "hash", primero.getNombreCompleto(), primero, fecha);
            var dos = new UserEntity(UUID.randomUUID(), segundo.getCorreo(), "hash", segundo.getNombreCompleto(), segundo, fecha.plusSeconds(1));
            dos.desactivar();
            var tres = new UserEntity(UUID.randomUUID(), tercero.getCorreo(), "hash", tercero.getNombreCompleto(), tercero, fecha.plusSeconds(2));
            usuarios.saveAllAndFlush(java.util.List.of(uno, dos, tres,
                    UserEntity.crearEjecutivo("admin@example.com", "hash", fecha.plusSeconds(3))));
        });
        var servicio = gestionUsuarios(sesiones);
        var estadisticas = factory.getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        var paginaUno = servicio.buscar(busqueda(request -> request.setTamanio(2)));
        assertThat(paginaUno.totalElementos()).isEqualTo(4);
        assertThat(paginaUno.totalPaginas()).isEqualTo(2);
        assertThat(paginaUno.contenido()).extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo)
                .containsExactly("admin@example.com", "tres@example.com");
        assertThat(paginaUno.contenido().get(0).telefono()).isNull();
        assertThat(paginaUno.contenido().get(1).telefono()).isEqualTo("5533333333");
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2); // pagina + count, sin consulta por usuario

        estadisticas.clear();
        var paginaDos = servicio.buscar(busqueda(request -> { request.setTamanio(2); request.setPagina(1); }));
        assertThat(paginaDos.totalElementos()).isEqualTo(4);
        assertThat(paginaDos.contenido()).extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo)
                .containsExactly("dos@example.com", "uno@example.com");
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);

        assertThat(servicio.buscar(busqueda(request -> request.setNombre("jUaN"))).contenido())
                .extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo)
                .containsExactly("dos@example.com", "uno@example.com");
        assertThat(servicio.buscar(busqueda(request -> { request.setNombre("jUaN"); request.setActivo(true); })).contenido())
                .extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo).containsExactly("uno@example.com");
        assertThat(servicio.buscar(busqueda(request -> request.setCorreo("DOS@EXAMPLE.COM"))).contenido())
                .extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo).containsExactly("dos@example.com");
        assertThat(servicio.buscar(busqueda(request -> request.setTelefono("5533333333"))).contenido())
                .extracting(com.proyecto.servicios.model.auth.UsuarioResponse::nombre).containsExactly(tercero.getNombreCompleto());
        assertThat(servicio.buscar(busqueda(request -> request.setActivo(false))).contenido())
                .extracting(com.proyecto.servicios.model.auth.UsuarioResponse::correo).containsExactly("dos@example.com");
        assertThat(servicio.buscar(busqueda(request -> {
            request.setNombre("jUaN"); request.setCorreo("UNO@EXAMPLE.COM");
            request.setTelefono("5511111111"); request.setActivo(true);
        })).contenido()).hasSize(1);

        estadisticas.clear();
        assertThat(servicio.buscar(busqueda(request -> {
            request.setNombre("Juan"); request.setTelefono("5533333333");
        })).totalElementos()).isZero();
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1); // sin fallback ni count innecesario
        for (String literal : java.util.List.of("%", "_", "\\", "' OR 1=1 --")) {
            assertThat(servicio.buscar(busqueda(request -> request.setNombre(literal))).contenido()).isEmpty();
        }
        assertThat(estadisticas.getEntityInsertCount()).isZero();
        assertThat(estadisticas.getEntityUpdateCount()).isZero();
        assertThat(estadisticas.getEntityDeleteCount()).isZero();
    }

    @Test
    void cambioDeContrasenaExigeNombreYCorreoExactosDelActorYSoloRevocaSuSesion() {
        String hash = encoder.encode("Actual123!");
        var propio = new UserEntity(UUID.randomUUID(), "propio@example.com", "propio@example.com", hash,
                "Marco Martinez", true, Instant.now());
        var homonimo = new UserEntity(UUID.randomUUID(), "ajeno@example.com", "ajeno@example.com", hash,
                "Marco Martinez", true, Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                usuarios.saveAllAndFlush(java.util.List.of(propio, homonimo)));
        UUID sesionPropia = crearSesion(propio.getId());
        UUID sesionAjena = crearSesion(homonimo.getId());
        var servicio = gestionUsuarios(sesiones);
        var credenciales = new CambiarContrasenaRequest("Actual123!", "Nueva456!");
        for (var selector : java.util.List.of(
                new ModificarContrasenaRequest("Marco", propio.getEmail(), credenciales),
                new ModificarContrasenaRequest("Maria Perez", propio.getEmail(), credenciales),
                new ModificarContrasenaRequest(propio.getFullName(), homonimo.getEmail(), credenciales),
                new ModificarContrasenaRequest("Marco%", propio.getEmail(), credenciales),
                new ModificarContrasenaRequest("' OR 1=1 --", propio.getEmail(), credenciales))) {
            assertThatThrownBy(() -> servicio.cambiarContrasena(selector, propio.getId()))
                    .isInstanceOf(com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException.class);
        }
        assertThatThrownBy(() -> servicio.cambiarContrasena(new ModificarContrasenaRequest(
                propio.getFullName(), propio.getEmail(), credenciales), homonimo.getId()))
                .isInstanceOf(com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException.class);
        assertThat(usuarios.findById(propio.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(usuarios.findById(homonimo.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(sesionValida(propio.getId(), sesionPropia)).isTrue();
        assertThat(sesionValida(homonimo.getId(), sesionAjena)).isTrue();

        servicio.cambiarContrasena(new ModificarContrasenaRequest(" MARCO MARTINEZ ", "PROPIO@EXAMPLE.COM",
                credenciales), propio.getId());
        assertThat(encoder.matches("Nueva456!", usuarios.findById(propio.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(usuarios.findById(homonimo.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(sesionValida(propio.getId(), sesionPropia)).isFalse();
        assertThat(sesionValida(homonimo.getId(), sesionAjena)).isTrue();
    }

    private UsuarioBusquedaRequest busqueda(java.util.function.Consumer<UsuarioBusquedaRequest> filtros) {
        var request = new UsuarioBusquedaRequest();
        filtros.accept(request);
        return request;
    }

    private ClienteEntity clienteDeBusqueda(String nombre, String correo, String telefono, int consecutivo, Instant fecha) {
        return new ClienteEntity(nombre, null, "Lopez", "Perez", LocalDate.of(1990, 1, 1),
                "LOPE900101HDFRRR0" + consecutivo, "LOPE900101AB" + consecutivo,
                Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null, correo, telefono,
                null, "Ingeniero", "Empresa", new BigDecimal("1000.00"), fecha);
    }

    private UsuarioService gestionUsuarios(UserSessionRepository sessionRepository) {
        return proxy(new UsuarioServiceImpl(usuarios, sessionRepository, encoder,
                mock(ApplicationEventPublisher.class)), UsuarioService.class);
    }

    private UUID crearSesion(UUID id) {
        UUID sesion = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                sesiones.saveAndFlush(new UserSessionEntity(id, sesion, Instant.now().plusSeconds(300), Instant.now())));
        return sesion;
    }

    private boolean sesionValida(UUID id, UUID sesion) {
        return new TransactionTemplate(transactionManager).execute(
                status -> sesiones.encontrarRolVigente(id, sesion, Instant.now()) != null);
    }

    private ClienteRegistroService registro(UserRepository userRepository) {
        var service = new ClienteRegistroServiceImpl(clientes, domicilios,
                new CuentaServiceImpl(cuentas, new CuentaProperties(), org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class)),
                org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class), userRepository, encoder);
        return proxy(service, ClienteRegistroService.class);
    }

    private <T> T proxy(Object service, Class<T> tipo) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("sfTransactionManager", transactionManager);
        interceptor.setBeanFactory(beans);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(interceptor);
        return tipo.cast(proxy.getProxy());
    }

    private RegistrarClienteRequest request() {
        return new RegistrarClienteRequest("Marco", null, "Morales", "Martinez",
                LocalDate.of(1990, 1, 1), "MOMA900101HDFRRR01", "MOMA900101AB1",
                Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null, "marco@example.com",
                "5512345678", null, "Ingeniero", "Empresa", new BigDecimal("1000.00"),
                new ActualizarDomicilioRequest("Reforma", "100", null, "Centro", "Cuauhtemoc",
                        "Ciudad de Mexico", "06000", "Mexico"), "Segura123!");
    }
}
