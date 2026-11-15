package com.proyecto.servicios.entity;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.Impl.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Persistencia y callbacks reales JPA; el bloqueo PostgreSQL se prueba por separado. */
class GestoPagoTokenPersistenceTest {
    private SessionFactory factory;
    private GestoPagoTokenRepository repository;
    private GestoPagoTokenCoordinator coordinator;
    private GestoPagoAuthClient client;
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    private final Instant issued = Instant.parse("2026-10-28T12:00:00Z");

    @BeforeEach
    void setup() {
        factory = new Configuration().addAnnotatedClass(GestoPagoToken.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop").buildSessionFactory();
        var tx = new TransactionTemplate(new JpaTransactionManager(factory));
        repository = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                .getRepository(GestoPagoTokenRepository.class);
        coordinator = mock(GestoPagoTokenCoordinator.class);
        when(coordinator.execute(anyInt(), anyString(), any())).thenAnswer(call ->
                tx.execute(status -> ((java.util.function.Supplier<?>) call.getArgument(2)).get()));
        client = mock(GestoPagoAuthClient.class);
        var response = new GestoPagoAuthResponse(); response.setToken("provider-secret"); response.setSuccess(true);
        when(client.authenticate(123, "device", "password-fixture")).thenReturn(response);
    }

    @AfterEach
    void close() { if (factory != null) { factory.close(); } }

    private GestoPagoTokenServiceImpl service(GestoPagoTokenCipher cipher) {
        return new GestoPagoTokenServiceImpl(client, repository, Mappers.getMapper(GestoPagoTokenMapper.class),
                123, "device", "password-fixture", Clock.fixed(issued, ZoneOffset.UTC), cipher, coordinator);
    }

    @Test
    void persisteSoloCifradoYReinicioReutilizaTokenSinAutenticar() {
        var cipher = new GestoPagoTokenCipher(key, "");
        assertThat(service(cipher).obtenerTokenVigente()).isEqualTo("provider-secret");
        var row = repository.findByIdDistribuidorAndCodigoDispositivo(123, "device").orElseThrow();
        assertThat(row.getToken()).doesNotContain("provider-secret").startsWith("gp-token:v1:");
        assertThat(row.getFechaActualizacion()).isEqualTo(LocalDateTime.ofInstant(issued, ZoneOffset.UTC));
        assertThat(service(cipher).obtenerTokenVigente()).isEqualTo("provider-secret");
        verify(client, times(1)).authenticate(anyInt(), anyString(), anyString());
    }

    @Test
    void recifradoRealConservaFechaDeEmisionYNoRenuevaProveedor() {
        service(new GestoPagoTokenCipher(key, "")).obtenerTokenVigente();
        byte[] next = new byte[32]; next[0] = 3;
        var rotated = new GestoPagoTokenCipher(Base64.getEncoder().encodeToString(next), key);
        assertThat(service(rotated).obtenerTokenVigente()).isEqualTo("provider-secret");
        var row = repository.findByIdDistribuidorAndCodigoDispositivo(123, "device").orElseThrow();
        assertThat(rotated.needsRotation(row.getToken())).isFalse();
        assertThat(row.getFechaActualizacion()).isEqualTo(LocalDateTime.ofInstant(issued, ZoneOffset.UTC));
        verify(client, times(1)).authenticate(anyInt(), anyString(), anyString());
    }
}
