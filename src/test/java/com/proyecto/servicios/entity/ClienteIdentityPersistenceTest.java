package com.proyecto.servicios.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteIdentityPersistenceTest {

    @Test
    void generaIdsNumericosYConservaLasRelaciones() throws Exception {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ClienteEntity.class)
                .addAnnotatedClass(DomicilioEntity.class)
                .addAnnotatedClass(CuentaEntity.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:cliente_identity;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");

        try (SessionFactory factory = configuration.buildSessionFactory();
             var session = factory.openSession()) {
            var transaction = session.beginTransaction();
            ClienteEntity primero = cliente("MOMA900101HDFRRR01", "MOMA900101AB1", "uno@example.com");
            ClienteEntity segundo = cliente("PELA920202HDFRRR02", "PELA920202AB2", "dos@example.com");
            assertThat(primero.getId()).isNull();
            session.persist(primero);
            session.persist(segundo);
            CuentaEntity cuenta = new CuentaEntity(
                    UUID.randomUUID(), primero, "CTA-0123456789ABCDEF0123", BigDecimal.ZERO, Instant.now()
            );
            session.persist(cuenta);
            session.persist(new DomicilioEntity(
                    UUID.randomUUID(), primero, "Reforma", "100", null, "Centro",
                    "Cuauhtemoc", "Ciudad de Mexico", "06000", "Mexico", Instant.now()
            ));
            transaction.commit();
            assertThat(primero.getId()).isEqualTo(1L);
            assertThat(segundo.getId()).isEqualTo(2L);
            session.clear();
            assertThat(session.find(CuentaEntity.class, cuenta.getId()).getCliente().getId()).isEqualTo(1L);
            assertThat(session.find(ClienteEntity.class, 1L).getCorreo()).isEqualTo("uno@example.com");

            var response = new ClienteResponseMapper().toResponse(primero, null, List.of());
            var json = new ObjectMapper().findAndRegisterModules().valueToTree(response);
            assertThat(json.get("id").isIntegralNumber()).isTrue();
            assertThat(json.get("id").asLong()).isEqualTo(1L);
        }
    }

    private ClienteEntity cliente(String curp, String rfc, String correo) {
        return new ClienteEntity(
                "Marco", null, "Morales", "Martinez", LocalDate.of(1990, 1, 1),
                curp, rfc, Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO, null,
                correo, "5512345678", null, "Ingeniero", "Empresa", new BigDecimal("1000.00"), Instant.now()
        );
    }
}
