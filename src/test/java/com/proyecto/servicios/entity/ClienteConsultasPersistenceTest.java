package com.proyecto.servicios.entity;

import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.mock;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteConsultasPersistenceTest {

    @BeforeEach
    void autenticarEjecutivo() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ejecutivo", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_EJECUTIVO"))));
    }

    @AfterEach
    void limpiarSesion() { SecurityContextHolder.clearContext(); }

    @Test
    void consultaFiltrosEnSqlConLimitesInclusivosYPaginacion() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ClienteEntity.class)
                .addAnnotatedClass(UserEntity.class)
                .addAnnotatedClass(UserSessionEntity.class)
                .addAnnotatedClass(DomicilioEntity.class)
                .addAnnotatedClass(CuentaEntity.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:cliente_consultas;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.generate_statistics", "true");

        try (SessionFactory factory = configuration.buildSessionFactory();
             var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            ClienteEntity primero = cliente("MOMA900101HDFRRR01", "MOMA900101AB1", "uno@example.com",
                    "2026-10-01T00:00:00Z");
            ClienteEntity segundo = cliente("PELA920202HDFRRR02", "PELA920202AB2", "dos@example.com",
                    "2026-10-03T23:59:59Z");
            ClienteEntity tercero = cliente("PELA930303HDFRRR03", "PELA930303AB3", "tres@example.com",
                    "2026-10-04T00:00:00Z");
            segundo.desactivar(Instant.parse("2026-10-04T00:00:00Z"));
            session.persist(primero);
            session.persist(segundo);
            session.persist(tercero);
            CuentaEntity activa = new CuentaEntity(UUID.randomUUID(), primero, "CTA-0123456789ABCDEF0123",
                    new BigDecimal("25.50"), Instant.now());
            CuentaEntity inactiva = new CuentaEntity(UUID.randomUUID(), segundo, "CTA-FFFFFFFFFFFFFFFFFFFF",
                    BigDecimal.ZERO, Instant.now());
            ReflectionTestUtils.setField(inactiva, "activa", false);
            session.persist(activa);
            session.persist(inactiva);
            session.flush();
            session.clear();

            JpaRepositoryFactory repositories = new JpaRepositoryFactory(session);
            CuentaRepository cuentas = repositories.getRepository(CuentaRepository.class);
            ClienteServiceImpl clientes = new ClienteServiceImpl(
                    repositories.getRepository(ClienteRepository.class),
                    repositories.getRepository(DomicilioRepository.class), cuentas, new ClienteResponseMapper(),
                    repositories.getRepository(UserRepository.class),
                    repositories.getRepository(UserSessionRepository.class), mock(ApplicationEventPublisher.class));
            CuentaServiceImpl cuentasService = new CuentaServiceImpl(cuentas, new CuentaProperties(),
                    new ClienteResponseMapper());

            factory.getStatistics().clear();
            var rango = clientes.consultarTodos(0, 20, null,
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
            assertThat(rango.contenido()).extracting(response -> response.correo())
                    .containsExactly("dos@example.com", "uno@example.com");
            // Pagina, conteo (si es necesario) y dos cargas por lotes; no una consulta por cliente.
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);

            session.clear();
            factory.getStatistics().clear();
            var todos = clientes.consultarTodos(0, 20);
            assertThat(todos.contenido()).hasSize(3);
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);
            assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
            assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
            assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();

            assertThat(clientes.consultarTodos(0, 20, true, null, null).totalElementos()).isEqualTo(2);
            assertThat(clientes.consultarTodos(0, 20, false, null, null).totalElementos()).isEqualTo(1);
            assertThat(clientes.consultarTodos(0, 1, null, LocalDate.of(2026, 10, 1), null).contenido())
                    .hasSize(1);
            assertThat(clientes.consultarTodos(0, 20, true, null, LocalDate.of(2026, 10, 3))
                    .totalElementos()).isEqualTo(1);
            assertThat(clientes.consultarPorCorreo(" UNO@EXAMPLE.COM ").id()).isEqualTo(primero.getId());
            assertThat(cuentasService.consultarActivas(0, 20).contenido())
                    .extracting(response -> response.numeroCuenta()).containsExactly("CTA-0123456789ABCDEF0123");
            assertThat(cuentasService.consultarPorNumeroCuenta("cta-0123456789abcdef0123").saldo())
                    .isEqualByComparingTo("25.50");
            session.flush();
            assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
            assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
            assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();
            session.clear();
            factory.getStatistics().clear();
            assertThat(cuentasService.consultarSaldo("cta-0123456789abcdef0123").saldo())
                    .isEqualByComparingTo("25.50");
            assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(1);
            assertThat(factory.getStatistics().getEntityLoadCount()).isZero();
            transaction.rollback();
        }
    }

    private ClienteEntity cliente(String curp, String rfc, String correo, String fecha) {
        return new ClienteEntity("Marco", null, "Morales", "Martinez", LocalDate.of(1990, 1, 1),
                curp, rfc, Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null,
                correo, "5512345678", null, "Ingeniero", "Empresa", new BigDecimal("1000.00"),
                Instant.parse(fecha));
    }
}
