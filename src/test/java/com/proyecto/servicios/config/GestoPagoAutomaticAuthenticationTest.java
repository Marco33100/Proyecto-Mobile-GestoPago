package com.proyecto.servicios.config;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.client.ProductCatalogClient;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.Impl.GestoPagoAccessTokenProvider;
import com.proyecto.servicios.service.Impl.GestoPagoTokenServiceImpl;
import com.proyecto.servicios.service.Impl.GestopagoProductGateway;
import feign.Client;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ejecuta contratos Feign, JSON/XML, servicios e interceptores; transporte simulado, sin red. */
class GestoPagoAutomaticAuthenticationTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class))
                .withUserConfiguration(Fixture.class)
                .withPropertyValues("app.database.enabled=true", "gestopago.auth.enabled=true",
                        "gestopago.auth.id-distribuidor=123", "gestopago.auth.codigo-dispositivo=fixture-device",
                        "gestopago.auth.password=fixture-password", "gestopago.auth.url=https://provider.fixture",
                        "product.service.url=https://provider.fixture", "product.service.failure-cooldown=30s",
                        "spring.cloud.openfeign.httpclient.hc5.enabled=false");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void tokenRechazadoRenuevaUnaVezYReintentaConNuevoHeader(int status) {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var transport = ctx.getBean(Transport.class);
            transport.failureStatus = status;
            transport.failureBody = "{\"token\":\"EXPIRED\"}";
            var gateway = ctx.getBean(GestopagoProductGateway.class);
            assertThat(gateway.fetchCatalog().getMensaje().getCodigo()).isEqualTo("01");
            assertThat(transport.authCalls.get()).isEqualTo(2);
            assertThat(transport.catalogCalls.get()).isEqualTo(2);
            gateway.fetchCatalog();
            assertThat(transport.authCalls.get()).isEqualTo(2);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"message\":\"Forbidden\"}", "not-json", "{\"token\":\"INVALID\"}"})
    void prohibicionSinMarcaExpiredNoRenuevaNiReintenta(String payload) {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var transport = ctx.getBean(Transport.class);
            transport.failureStatus = 403;
            transport.failureBody = payload;
            var gateway = ctx.getBean(GestopagoProductGateway.class);
            assertThatThrownBy(gateway::fetchCatalog).isInstanceOf(ProductIntegrationException.class);
            assertThat(transport.authCalls.get()).isEqualTo(1);
            assertThat(transport.catalogCalls.get()).isEqualTo(1);
            assertThatThrownBy(gateway::fetchCatalog).isInstanceOf(ProductIntegrationException.class);
            assertThat(transport.catalogCalls.get()).isEqualTo(1); // cooldown preservado
        });
    }

    @Test
    void segundoRechazoNoEntraEnBucle() {
        context().run(ctx -> {
            var transport = ctx.getBean(Transport.class);
            transport.failureStatus = 401;
            transport.alwaysReject = true;
            assertThatThrownBy(ctx.getBean(GestopagoProductGateway.class)::fetchCatalog)
                    .isInstanceOf(ProductIntegrationException.class);
            assertThat(transport.authCalls.get()).isEqualTo(2);
            assertThat(transport.catalogCalls.get()).isEqualTo(2);
        });
    }

    @Test
    void modoManualUsaTokenConfiguradoSinAutenticacionExterna() {
        context().withPropertyValues("gestopago.auth.enabled=false").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var transport = ctx.getBean(Transport.class);
            ctx.getBean(GestopagoProductGateway.class).fetchCatalog();
            assertThat(transport.authCalls.get()).isZero();
            assertThat(transport.catalogCalls.get()).isEqualTo(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = {GestoPagoAuthClient.class, ProductCatalogClient.class})
    @Import({GestoPagoTokenServiceImpl.class, GestoPagoAccessTokenProvider.class, GestopagoProductGateway.class})
    static class Fixture {
        @Bean ProductServiceProperties properties() {
            var properties = new ProductServiceProperties();
            properties.setApiKey("fixture-api-key");
            properties.setBearerToken("fixture-fixed-token");
            return properties;
        }
        @Bean GestoPagoTokenRepository repository() { return mock(GestoPagoTokenRepository.class); }
        @Bean GestoPagoTokenMapper mapper() { return Mappers.getMapper(GestoPagoTokenMapper.class); }
        @Bean Transport transport() { return new Transport(); }
        @Bean Client client(Transport transport) { return transport.client(); }
    }

    static class Transport {
        final AtomicInteger authCalls = new AtomicInteger();
        final AtomicInteger catalogCalls = new AtomicInteger();
        int failureStatus;
        String failureBody = "{}";
        boolean alwaysReject;

        Client client() {
            return (request, options) -> {
                assertThat(request.headers().get("X-API-Key")).containsExactly("fixture-api-key");
                if (request.url().contains("/authenticate/")) {
                    assertThat(request.httpMethod().name()).isEqualTo("POST");
                    assertThat(request.url()).contains("idDistribuidor=123", "codigoDispositivo=fixture-device",
                            "password=fixture-password");
                    int issued = authCalls.incrementAndGet();
                    return Response.builder().status(200).reason("OK").request(request)
                            .headers(Map.of("Content-Type", List.of("application/json")))
                            .body("{\"success\":true,\"token\":\"fixture-token-" + issued + "\"}",
                                    StandardCharsets.UTF_8).build();
                }
                assertThat(request.httpMethod().name()).isEqualTo("GET");
                assertThat(request.url()).endsWith("/sistema/service/getProductList.do");
                String expected = authCalls.get() == 0 ? "fixture-fixed-token" : "fixture-token-" + authCalls.get();
                assertThat(request.headers().get("Authorization")).containsExactly("Bearer " + expected);
                int called = catalogCalls.incrementAndGet();
                if (failureStatus > 0 && (alwaysReject || called == 1)) {
                    return Response.builder().status(failureStatus).reason("Rejected").request(request)
                            .headers(Map.of("Content-Type", List.of("application/json")))
                            .body(failureBody, StandardCharsets.UTF_8).build();
                }
                return Response.builder().status(200).reason("OK").request(request)
                        .headers(Map.of("Content-Type", List.of("application/xml")))
                        .body("<RESPONSE><MENSAJE><CODIGO>01</CODIGO><TEXTO>Fixture</TEXTO>"
                                + "</MENSAJE><PRODUCTOS/></RESPONSE>", StandardCharsets.UTF_8).build();
            };
        }
    }
}
