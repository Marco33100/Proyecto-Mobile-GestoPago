package com.proyecto.servicios.entity;

import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CuentaPropietarioPersistenceTest {
    private static final String PROPIA = "CTA-00000000000000000001";
    private static final String INACTIVA = "CTA-00000000000000000002";
    private static final String AJENA = "CTA-00000000000000000003";
    private static final String INEXISTENTE = "CTA-FFFFFFFFFFFFFFFFFFFF";
    private SessionFactory factory;
    private Session session;
    private Transaction transaction;
    private CuentaServiceImpl service;
    private UserEntity primero;
    private UserEntity segundo;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ClienteEntity.class).addAnnotatedClass(CuentaEntity.class)
                .addAnnotatedClass(UserEntity.class).addAnnotatedClass(UserSessionEntity.class)
                .addAnnotatedClass(DomicilioEntity.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID());
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.generate_statistics", "true");
        factory = configuration.buildSessionFactory();
        session = factory.openSession();
        transaction = session.beginTransaction();
        ClienteEntity clienteA = cliente("MOMA900101HDFRRR01", "MOMA900101AB1", "uno@example.com");
        ClienteEntity clienteB = cliente("PELA920202HDFRRR02", "PELA920202AB2", "dos@example.com");
        session.persist(clienteA);
        session.persist(clienteB);
        primero = new UserEntity(UUID.randomUUID(), clienteA.getCorreo(), "hash", "Cliente A", clienteA, Instant.now());
        segundo = new UserEntity(UUID.randomUUID(), clienteB.getCorreo(), "hash", "Cliente B", clienteB, Instant.now());
        session.persist(primero);
        session.persist(segundo);
        session.persist(new CuentaEntity(UUID.randomUUID(), clienteA, PROPIA, new BigDecimal("25.50"), Instant.now()));
        CuentaEntity inactiva = new CuentaEntity(UUID.randomUUID(), clienteA, INACTIVA, new BigDecimal("11.25"), Instant.now());
        inactiva.desactivar(Instant.now());
        session.persist(inactiva);
        session.persist(new CuentaEntity(UUID.randomUUID(), clienteB, AJENA, new BigDecimal("999.99"), Instant.now()));
        session.flush();
        session.clear();
        CuentaRepository repository = new JpaRepositoryFactory(session).getRepository(CuentaRepository.class);
        service = new CuentaServiceImpl(repository, new CuentaProperties(), org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class));
        autenticar(primero);
        factory.getStatistics().clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (transaction != null && transaction.isActive()) transaction.rollback();
        if (session != null) session.close();
        if (factory != null) factory.close();
    }

    @Test
    void lecturaYPaginacionSoloIncluyenCuentasPropiasSinEscriturasNiCargaDePropietarios() {
        assertThat(service.consultarPorNumeroCuenta(PROPIA).saldo()).isEqualByComparingTo("25.50");
        assertThat(service.consultarPorNumeroCuenta(INACTIVA).activa()).isFalse();
        assertThat(service.consultarSaldo(INACTIVA).saldo()).isEqualByComparingTo("11.25");
        var pagina = service.consultarPropias(0, 1);
        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        assertThat(pagina.contenido()).extracting(CuentaResponse::numeroCuenta).containsExactly(PROPIA);
        assertThat(service.consultarPropias(1, 1).contenido())
                .extracting(CuentaResponse::numeroCuenta).containsExactly(INACTIVA);
        assertThat(service.consultarPropias(0, 20).contenido())
                .extracting(CuentaResponse::numeroCuenta).containsExactly(PROPIA, INACTIVA);
        session.flush();
        assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
        assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
        assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();
        assertThat(factory.getStatistics().getEntityFetchCount()).isZero();
    }

    @Test
    void cambiarNumeroNoPermiteLeerOtraCuentaYErroresNoRevelanExistencia() {
        for (String numero : List.of(AJENA, INEXISTENTE)) {
            assertThatThrownBy(() -> service.consultarPorNumeroCuenta(numero))
                    .isInstanceOf(CuentaNoEncontradaException.class).hasMessage("No existe la cuenta solicitada");
            assertThatThrownBy(() -> service.consultarSaldo(numero))
                    .isInstanceOf(CuentaNoEncontradaException.class).hasMessage("No existe la cuenta solicitada");
        }
        assertThat(factory.getStatistics().getEntityLoadCount()).isZero();
        autenticar(segundo);
        assertThat(service.consultarSaldo(AJENA).saldo()).isEqualByComparingTo("999.99");
        assertThat(service.consultarPropias(0, 20).contenido())
                .extracting(CuentaResponse::numeroCuenta).containsExactly(AJENA);
        assertThatThrownBy(() -> service.consultarSaldo(PROPIA)).isInstanceOf(CuentaNoEncontradaException.class);
    }

    @Test
    void saldoPropioMantieneUnaSolaConsultaSinCargarEntidades() {
        assertThat(service.consultarSaldo(PROPIA).saldo()).isEqualByComparingTo("25.50");
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(1);
        assertThat(factory.getStatistics().getEntityLoadCount()).isZero();
    }

    @Test
    void usuarioSinVinculoConClienteNoObtieneCuentas() {
        UserEntity sinCliente = UserEntity.crearEjecutivo("sincliente@example.com", "hash", Instant.now());
        session.persist(sinCliente);
        session.flush();
        session.clear();
        // Incluso una identidad autenticada sin relacion de propiedad no puede ver cuentas.
        autenticar(sinCliente);
        assertThat(service.consultarPropias(0, 20).contenido()).isEmpty();
        assertThatThrownBy(() -> service.consultarSaldo(PROPIA)).isInstanceOf(CuentaNoEncontradaException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.consultarSaldo(PROPIA))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void ejecutivoConservaAccesoAdministrativoACualquierCuenta() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ejecutivo", null, List.of(new SimpleGrantedAuthority("ROLE_EJECUTIVO"))));
        assertThat(service.consultarPorNumeroCuenta(AJENA).saldo()).isEqualByComparingTo("999.99");
        assertThat(service.consultarSaldo(PROPIA).saldo()).isEqualByComparingTo("25.50");
        assertThat(service.consultarActivas(0, 20).contenido())
                .extracting(CuentaResponse::numeroCuenta).containsExactly(PROPIA, AJENA);
    }

    private void autenticar(UserEntity usuario) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(usuario.getId(), UUID.randomUUID(), usuario.getEmail(), usuario.getIdentifier()),
                null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    private ClienteEntity cliente(String curp, String rfc, String correo) {
        return new ClienteEntity("Marco", null, "Morales", "Martinez", LocalDate.of(1990, 1, 1),
                curp, rfc, Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null,
                correo, "5512345678", null, "Ingeniero", "Empresa", new BigDecimal("1000.00"), Instant.now());
    }
}
