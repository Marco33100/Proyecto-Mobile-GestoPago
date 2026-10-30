package com.proyecto.servicios.service.Impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** El bloqueo transaccional incluye la primera insercion y se libera aun ante fallos. */
@Component
@ConditionalOnProperty(name = {"app.database.enabled", "gestopago.auth.enabled"}, havingValue = "true")
public class GestoPagoTokenCoordinator {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public GestoPagoTokenCoordinator(@Qualifier("sfDatasource") DataSource dataSource,
                                    @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(20);
    }

    public <T> T execute(int distributor, String device, Supplier<T> work) {
        long lock = lockId(distributor, device);
        return transaction.execute(status -> {
            // No esperar ocupando el pool mientras otra replica autentica. El caller recibe un error seguro.
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lock))) {
                throw new IllegalStateException("Renovacion del proveedor en curso");
            }
            return work.get();
        });
    }

    static long lockId(int distributor, String device) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("gestopago-token/v1/" + distributor + "/" + device).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (Exception ignored) {
            throw new IllegalStateException("No fue posible coordinar la autenticacion del proveedor");
        }
    }
}
