package com.proyecto.servicios.entity;

import com.proyecto.servicios.service.Impl.GestoPagoTokenCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Dos coordinadores, conexiones reales y sin llamadas ni credenciales del proveedor. */
@EnabledIfEnvironmentVariable(named = "ONBOARDING_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
class GestoPagoTokenCoordinationPostgresTest {
    @Test
    void bloqueoEntreReplicasSeLiberaAlConfirmarYAlRevertir() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("ONBOARDING_TEST_JDBC_URL"),
                System.getenv("ONBOARDING_TEST_DB_USER"), System.getenv("ONBOARDING_TEST_DB_PASSWORD"));
        var first = new GestoPagoTokenCoordinator(ds, new DataSourceTransactionManager(ds));
        var second = new GestoPagoTokenCoordinator(ds, new DataSourceTransactionManager(ds));
        String identity = "test-" + java.util.UUID.randomUUID();
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> first.execute(123, identity, () -> {
                acquired.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Test timeout"); }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
                return "first";
            }));
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.execute(123, identity, () -> "must-not-run"))
                    .isInstanceOf(IllegalStateException.class);
            release.countDown();
            assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.execute(123, identity, () -> "second")).isEqualTo("second");
            assertThatThrownBy(() -> first.execute(123, identity, () -> { throw new IllegalStateException("rollback"); }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(second.execute(123, identity, () -> "released")).isEqualTo("released");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
