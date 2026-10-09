package com.proyecto.servicios.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.AuthConfiguration;
import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.config.ExecutiveBootstrapProperties;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.exception.auth.InvalidCredentialsException;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.ContrasenaInvalidaException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
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
                    .locations("classpath:db/initial").baselineOnMigrate(false).load().migrate();
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
        assertThat(usuarios.findUserById(ejecutivo.getId()).getPasswordHash()).isEqualTo(hashOriginal);
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

        gestionUsuarios(sesiones).cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest(request().contrasena(), "Nueva456!"));

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

        assertThatThrownBy(() -> gestionUsuarios(falla).cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest(request().contrasena(), "Nueva456!")))
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
        assertThatThrownBy(() -> gestionUsuarios(sesiones).cambiarContrasena(usuario.getId(), usuario.getId(),
                new CambiarContrasenaRequest("Incorrecta123!", "Nueva456!")))
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
                new ClienteResponseMapper()), CuentaService.class);
        var cuenta = cuentas.findAllByClienteId(cliente.id()).get(0);
        var response = servicioCuentas.desactivar(cuenta.getNumeroCuenta());
        assertThat(response.activa()).isFalse();
        assertThat(response.saldo()).isEqualByComparingTo(cuenta.getSaldo());
        assertThat(clientes.findClienteById(cliente.id()).isActivo()).isTrue();
        assertThat(usuarios.findUserById(usuario.getId()).isEnabled()).isTrue();
        assertThat(sesionValida(usuario.getId(), sesion)).isTrue();
        assertThat(gestionUsuarios(sesiones).consultar(usuario.getId(), usuario.getId())).isNotNull();
        JwtTokenService jwt = mock(JwtTokenService.class);
        when(jwt.generate(any(), any())).thenReturn(new JwtTokenService.TokenResult(
                "token-de-prueba", Instant.now().plusSeconds(300)));
        AuthService auth = proxy(new AuthServiceImpl(usuarios, encoder, jwt,
                mock(ActiveSessionService.class)), AuthService.class);
        assertThat(auth.login(new LoginRequest(usuario.getEmail(), request().contrasena())).accessToken())
                .isEqualTo("token-de-prueba");
        assertThat(servicioCuentas.consultarPorNumeroCuenta(cuenta.getNumeroCuenta()).activa()).isFalse();
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
            usuarios.findUserById(usuario.getId()).desactivar();
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
        assertThat(usuarios.existsByEmail("legado@example.com")).isTrue();
        assertThat(usuarios.findByEmail("legado@example.com")).isPresent();
        assertThat(usuarios.findByEmail("ausente@example.com")).isEmpty();

        var cliente = registro(usuarios).registrar(request());
        assertThat(clientes.existsByCorreoAndIdNot("marco@example.com", cliente.id())).isFalse();
        assertThat(clientes.existsByCorreoAndIdNot("marco@example.com", cliente.id() + 1)).isTrue();
        assertThat(clientes.existsByCorreoAndIdNot("ausente@example.com", cliente.id())).isFalse();
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
                status -> sesiones.existeSesionValida(id, sesion, Instant.now()));
    }

    private ClienteRegistroService registro(UserRepository userRepository) {
        var service = new ClienteRegistroServiceImpl(clientes, domicilios,
                new CuentaServiceImpl(cuentas, new CuentaProperties(), new ClienteResponseMapper()),
                new ClienteResponseMapper(), userRepository, encoder);
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
