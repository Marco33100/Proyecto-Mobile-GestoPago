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
import com.proyecto.servicios.model.cliente.ConsultaClientesRequest;
import java.util.function.Consumer;
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
                    repositories.getRepository(DomicilioRepository.class), cuentas, org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class),
                    repositories.getRepository(UserRepository.class),
                    repositories.getRepository(UserSessionRepository.class), mock(ApplicationEventPublisher.class));
            CuentaServiceImpl cuentasService = new CuentaServiceImpl(cuentas, new CuentaProperties(),
                    org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class));

            factory.getStatistics().clear();
            var rango = clientes.consultar(consulta(r -> {
                r.setDesde(LocalDate.of(2026, 10, 1));
                r.setHasta(LocalDate.of(2026, 10, 3));
            }));
            assertThat(rango.contenido()).extracting(response -> response.correo())
                    .containsExactly("dos@example.com", "uno@example.com");
            // Pagina, conteo (si es necesario) y dos cargas por lotes; no una consulta por cliente.
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);

            session.clear();
            factory.getStatistics().clear();
            var todos = clientes.consultar(new ConsultaClientesRequest());
            assertThat(todos.contenido()).hasSize(3);
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);
            assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
            assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
            assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();

            assertThat(clientes.consultar(consulta(r -> r.setActivo(true))).totalElementos()).isEqualTo(2);
            assertThat(clientes.consultar(consulta(r -> r.setActivo(false))).totalElementos()).isEqualTo(1);
            assertThat(clientes.consultar(consulta(r -> {
                r.setTamanio(1);
                r.setDesde(LocalDate.of(2026, 10, 1));
            })).contenido())
                    .hasSize(1);
            assertThat(clientes.consultar(consulta(r -> {
                r.setActivo(true);
                r.setHasta(LocalDate.of(2026, 10, 3));
            }))
                    .totalElementos()).isEqualTo(1);
            assertThat(clientes.consultar(consulta(r -> r.setCorreo(" UNO@EXAMPLE.COM "))).contenido())
                    .extracting(response -> response.id()).containsExactly(primero.getId());
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
            session.clear();
            factory.getStatistics().clear();
            var parciales = clientes.consultar(consulta(r -> {
                r.setTamanio(2);
                r.setNombre(" mAr ");
            }));
            assertThat(parciales.totalElementos()).isEqualTo(3);
            assertThat(parciales.contenido()).hasSize(2);
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);
            assertThat(clientes.consultar(consulta(r -> {
                r.setNombre("noexiste");
                r.setRfc("pElA");
                r.setCurp("mOmA");
            })).contenido())
                    .extracting(response -> response.correo())
                    .containsExactly("tres@example.com", "dos@example.com", "uno@example.com");
            assertThat(clientes.consultar(consulta(r -> {
                r.setNombre("noexiste");
                r.setRfc("pElA");
            })).totalElementos()).isEqualTo(2);
            assertThat(clientes.consultar(consulta(r -> r.setCurp("mOmA"))).totalElementos()).isEqualTo(1);
            assertThat(clientes.consultar(consulta(r -> {
                r.setNombre("mAr");
                r.setRfc("pElA");
                r.setActivo(false);
            })).contenido())
                    .extracting(response -> response.correo()).containsExactly("dos@example.com");
            assertThat(clientes.consultar(consulta(r -> {
                r.setTamanio(1);
                r.setNombre("ninguno");
                r.setActivo(false);
            })).contenido())
                    .extracting(response -> response.correo()).containsExactly("dos@example.com");
            var paginaSinCoincidencias = clientes.consultar(consulta(r -> {
                r.setTamanio(1);
                r.setNombre("ninguno");
            }));
            assertThat(paginaSinCoincidencias.totalElementos()).isEqualTo(3);
            assertThat(paginaSinCoincidencias.contenido()).hasSize(1);
            var fueraDeRango = clientes.consultar(consulta(r -> {
                r.setPagina(3);
                r.setTamanio(1);
                r.setRfc("pElA");
            }));
            assertThat(fueraDeRango.totalElementos()).isEqualTo(2);
            assertThat(fueraDeRango.contenido()).isEmpty();
            assertThat(clientes.consultar(consulta(r -> {
                r.setNombre("   ");
                r.setRfc("");
            })).totalElementos()).isEqualTo(3);
            session.clear();
            factory.getStatistics().clear();
            // % se busca literalmente: primero no hay coincidencias y luego se ejecuta el fallback vigente.
            assertThat(clientes.consultar(consulta(r -> r.setNombre("%"))).totalElementos()).isEqualTo(3);
            assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(4);
            var consulta = new com.proyecto.servicios.model.cliente.ConsultaClientesRequest();
            consulta.setId(primero.getId());
            consulta.setCorreo(" UNO@EXAMPLE.COM ");
            consulta.setNumeroCuenta("cta-0123456789abcdef0123");
            consulta.setNombre("Marco");
            session.clear();
            factory.getStatistics().clear();
            assertThat(clientes.consultar(consulta).contenido()).extracting(response -> response.id())
                    .containsExactly(primero.getId());
            assertThat(factory.getStatistics().getPrepareStatementCount()).isLessThanOrEqualTo(4);
            // La combinacion de identificadores es AND: no permite devolver el registro de otro criterio.
            consulta.setCorreo("dos@example.com");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> clientes.consultar(consulta))
                    .isInstanceOf(com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException.class);
            consulta.setCorreo(null);
            consulta.setNombre("noexiste");
            // El fallback del texto nunca elimina el identificador exacto.
            assertThat(clientes.consultar(consulta).contenido()).extracting(response -> response.id())
                    .containsExactly(primero.getId());
            consulta.setPagina(1);
            consulta.setTamanio(1);
            assertThat(clientes.consultar(consulta).contenido()).isEmpty();
            assertThat(clientes.consultar(consulta).totalElementos()).isEqualTo(1);
            consulta.setPagina(0);
            consulta.setId(null);
            consulta.setNombre(null);
            assertThat(clientes.consultar(consulta).contenido()).extracting(response -> response.id())
                    .containsExactly(primero.getId());
            consulta.setNumeroCuenta("CTA-AAAAAAAAAAAAAAAAAAAA");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> clientes.consultar(consulta))
                    .isInstanceOf(com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException.class);
            session.flush();
            assertThat(factory.getStatistics().getEntityInsertCount()).isZero();
            assertThat(factory.getStatistics().getEntityUpdateCount()).isZero();
            assertThat(factory.getStatistics().getEntityDeleteCount()).isZero();
            transaction.rollback();
        }
    }

    private ClienteEntity cliente(String curp, String rfc, String correo, String fecha) {
        return new ClienteEntity("Marco", null, "Morales", "Martinez", LocalDate.of(1990, 1, 1),
                curp, rfc, Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null,
                correo, "5512345678", null, "Ingeniero", "Empresa", new BigDecimal("1000.00"),
                Instant.parse(fecha));
    }
    private ConsultaClientesRequest consulta(Consumer<ConsultaClientesRequest> filtros) {
        var request = new ConsultaClientesRequest();
        filtros.accept(request);
        return request;
    }

}
