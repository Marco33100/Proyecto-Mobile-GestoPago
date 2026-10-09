package com.proyecto.servicios.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.Impl.GestoPagoTokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestoPagoTokenLogTest {
    @Test
    void fallaDeAutenticacionNoRegistraQueryNiCausaNiToken() {
        var client = mock(GestoPagoAuthClient.class);
        var repository = mock(GestoPagoTokenRepository.class);
        when(client.authenticate(123, "equipo-prueba", "clave-prueba"))
                .thenThrow(new IllegalStateException("https://proveedor?password=clave-prueba token-secreto"));
        var logger = (Logger) LoggerFactory.getLogger(GestoPagoTokenServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            new GestoPagoTokenServiceImpl(client, repository, mock(GestoPagoTokenMapper.class),
                    123, "equipo-prueba", "clave-prueba").renovarToken();
            assertThat(appender.list).isNotEmpty();
            appender.list.forEach(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("clave-prueba", "token-secreto", "password=");
                assertThat(event.getThrowableProxy()).isNull();
            });
            verifyNoInteractions(repository);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
