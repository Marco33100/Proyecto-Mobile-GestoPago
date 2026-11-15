package com.proyecto.servicios.config;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import feign.Feign;
import feign.Logger;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class GestoPagoAuthClientConfigurationTest {
    private final GestoPagoAuthClientConfiguration configuration = new GestoPagoAuthClientConfiguration();

    @Test
    void conservaContratoQueryPeroErrorYLoggerNoExponenSecretos() {
        var client = Feign.builder().contract(new SpringMvcContract())
                .logger(configuration.authLogger()).logLevel(Logger.Level.FULL)
                .requestInterceptor(configuration.authHttpsOnly())
                .errorDecoder(configuration.authErrorDecoder())
                .client((request, options) -> {
                    assertThat(request.url()).contains("idDistribuidor=123", "codigoDispositivo=equipo-prueba", "password=clave-prueba");
                    return Response.builder().status(401).reason("Unauthorized").headers(Map.of())
                            .request(request).body("clave-prueba token-secreto", StandardCharsets.UTF_8).build();
                }).target(GestoPagoAuthClient.class, "https://proveedor.example");
        assertThat(configuration.authLogger()).isInstanceOf(Logger.NoOpLogger.class);
        assertThatThrownBy(() -> client.authenticate(123, "equipo-prueba", "clave-prueba"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 401")
                .hasMessageNotContaining("clave-prueba").hasMessageNotContaining("token-secreto")
                .hasMessageNotContaining("proveedor.example").hasNoCause();
    }

    @Test
    void noEnviaCredencialesPorHttp() {
        var invoked = new AtomicBoolean();
        var client = Feign.builder().contract(new SpringMvcContract())
                .requestInterceptor(configuration.authHttpsOnly())
                .client((request, options) -> { invoked.set(true); throw new AssertionError("No debe enviar HTTP"); })
                .target(GestoPagoAuthClient.class, "http://proveedor.example");
        assertThatThrownBy(() -> client.authenticate(123, "equipo-prueba", "clave-prueba"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
        assertThat(invoked).isFalse();
    }
}
