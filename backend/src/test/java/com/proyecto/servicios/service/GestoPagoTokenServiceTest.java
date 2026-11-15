package com.proyecto.servicios.service;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.Impl.GestoPagoTokenServiceImpl;
import com.proyecto.servicios.service.Impl.GestoPagoTokenCipher;
import com.proyecto.servicios.service.Impl.GestoPagoTokenCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestoPagoTokenServiceTest {
    private GestoPagoAuthClient client;
    private GestoPagoTokenRepository repository;
    private Clock clock;
    private GestoPagoTokenServiceImpl service;
    private GestoPagoTokenCipher cipher;
    private GestoPagoTokenCoordinator coordinator;
    private final Instant now = Instant.parse("2026-10-10T05:00:00Z");

    @BeforeEach
    void setUp() {
        client = mock(GestoPagoAuthClient.class);
        repository = mock(GestoPagoTokenRepository.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        cipher = new GestoPagoTokenCipher(java.util.Base64.getEncoder().encodeToString(new byte[32]), "");
        coordinator = mock(GestoPagoTokenCoordinator.class);
        Object lock = new Object();
        when(coordinator.execute(eq(123), eq("fixture-device"), any())).thenAnswer(call -> {
            synchronized (lock) { return ((java.util.function.Supplier<?>) call.getArgument(2)).get(); }
        });
        var persisted = new java.util.concurrent.atomic.AtomicReference<GestoPagoToken>();
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenAnswer(call -> Optional.ofNullable(persisted.get()));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            GestoPagoToken entity = call.getArgument(0);
            persisted.set(entity);
            return entity;
        });
        service = new GestoPagoTokenServiceImpl(client, repository,
                Mappers.getMapper(GestoPagoTokenMapper.class), 123, "fixture-device", "fixture-password", clock,
                cipher, coordinator);
    }

    @Test
    void obtieneUnaVezYReutilizaTokenOpacoSinInterpretarloComoJwt() {
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenReturn(response("opaque+token/fixture=="));
        assertThat(service.obtenerTokenVigente()).isEqualTo("opaque+token/fixture==");
        assertThat(service.obtenerTokenVigente()).isEqualTo("opaque+token/fixture==");
        verify(client, times(1)).authenticate(123, "fixture-device", "fixture-password");
        verify(repository).saveAndFlush(argThat(entity -> !entity.getToken().contains("opaque+token/fixture==")
                && cipher.decrypt(entity.getToken(), 123, "fixture-device").equals("opaque+token/fixture==")
                && entity.getExpiresIn() == 86400
                && entity.getActivo() && "Bearer".equals(entity.getTokenType())));
    }

    @Test
    void renuevaBajoDemandaTras24HorasSinDependerDeScheduler() {
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenReturn(response("old"), response("new"));
        assertThat(service.obtenerTokenVigente()).isEqualTo("old");
        when(clock.instant()).thenReturn(now.plusSeconds(86400));
        assertThat(service.obtenerTokenVigente()).isEqualTo("new");
        verify(client, times(2)).authenticate(123, "fixture-device", "fixture-password");
    }

    @Test
    void respetaExpiresInMasCortoSinConservarLaDuracionDelTokenAnterior() {
        var first = response("old");
        first.setExpiresIn(60L);
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenReturn(first, response("new"));
        assertThat(service.obtenerTokenVigente()).isEqualTo("old");
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThat(service.obtenerTokenVigente()).isEqualTo("new");
    }

    @Test
    void reinicioReutilizaTokenPersistidoVigente() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenReturn(Optional.of(stored("persisted", now.minusSeconds(3600))));
        assertThat(service.obtenerTokenVigente()).isEqualTo("persisted");
        verifyNoInteractions(client);
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "inactive", "unknown-expiry", "future"})
    void noReutilizaRegistrosSinVigenciaConfiable(String invalidity) {
        var token = stored("invalid", now.minusSeconds(90000));
        if (invalidity.equals("inactive")) { token.setActivo(false); }
        if (invalidity.equals("unknown-expiry")) { token.setExpiresIn(null); }
        if (invalidity.equals("future")) { token.setFechaActualizacion(LocalDateTime.ofInstant(now.plusSeconds(60), ZoneOffset.UTC)); }
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenReturn(Optional.of(token));
        when(client.authenticate(123, "fixture-device", "fixture-password")).thenReturn(response("new"));
        assertThat(service.obtenerTokenVigente()).isEqualTo("new");
    }

    @Test
    void rechazoRenuevaYConsultasQueUsaronTokenViejoReutilizanLaRenovacion() {
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenReturn(response("old"), response("new"));
        assertThat(service.obtenerTokenVigente()).isEqualTo("old");
        assertThat(service.renovarTokenRechazado("old")).isEqualTo("new");
        assertThat(service.renovarTokenRechazado("old")).isEqualTo("new");
        verify(client, times(2)).authenticate(123, "fixture-device", "fixture-password");
    }

    @Test
    void consultasConcurrentesCompartenUnaAutenticacion() throws Exception {
        when(client.authenticate(123, "fixture-device", "fixture-password")).thenReturn(response("shared"));
        var replica = new GestoPagoTokenServiceImpl(client, repository, Mappers.getMapper(GestoPagoTokenMapper.class),
                123, "fixture-device", "fixture-password", clock, cipher, coordinator);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<Callable<String>>();
            for (int i = 0; i < 20; i++) {
                calls.add(i % 2 == 0 ? service::obtenerTokenVigente : replica::obtenerTokenVigente);
            }
            for (var result : executor.invokeAll(calls)) { assertThat(result.get()).isEqualTo("shared"); }
            verify(client, times(1)).authenticate(123, "fixture-device", "fixture-password");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void bloqueoOcupadoNoAutenticaEnParaleloYNoExponeDetalles() {
        when(coordinator.execute(eq(123), eq("fixture-device"), any()))
                .thenThrow(new IllegalStateException("internal-sensitive-detail"));
        assertThatThrownBy(service::obtenerTokenVigente).isInstanceOf(ProductIntegrationException.class)
                .hasMessageNotContaining("internal-sensitive-detail").hasNoCause();
        verifyNoInteractions(client);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void baseIndisponibleNoIniciaAutenticacionNiDevuelveTokenEnCache() {
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenThrow(new IllegalStateException("connection-password=secret"));
        assertThatThrownBy(service::obtenerTokenVigente).isInstanceOf(ProductIntegrationException.class)
                .hasMessageNotContaining("secret").hasNoCause();
        verifyNoInteractions(client);
    }

    @Test
    void replicasReleenTokenCompartidoYNoRenuevanDosVecesUnRechazo() {
        var replica = new GestoPagoTokenServiceImpl(client, repository, Mappers.getMapper(GestoPagoTokenMapper.class),
                123, "fixture-device", "fixture-password", clock, cipher, coordinator);
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenReturn(response("old"), response("new"));
        assertThat(service.obtenerTokenVigente()).isEqualTo("old");
        assertThat(replica.obtenerTokenVigente()).isEqualTo("old");
        assertThat(service.renovarTokenRechazado("old")).isEqualTo("new");
        assertThat(replica.renovarTokenRechazado("old")).isEqualTo("new");
        assertThat(replica.obtenerTokenVigente()).isEqualTo("new");
        verify(client, times(2)).authenticate(anyInt(), anyString(), anyString());
    }

    @Test
    void textoPlanoOClaveIncorrectaNoProvocaReautenticacionNiFiltraElToken() {
        var entity = stored("private-token", now);
        entity.setToken("private-token");
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenReturn(Optional.of(entity));
        assertThatThrownBy(service::obtenerTokenVigente).isInstanceOf(ProductIntegrationException.class)
                .hasMessageNotContaining("private-token").hasNoCause();
        verifyNoInteractions(client);
    }

    @Test
    void rotacionNoProlongaVigenciaNiAutenticaOtraVez() {
        byte[] other = new byte[32];
        java.util.Arrays.fill(other, (byte) 7);
        var rotated = new GestoPagoTokenCipher(java.util.Base64.getEncoder().encodeToString(other),
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        var entity = stored("persisted", now.minusSeconds(3600));
        var issuedAt = entity.getFechaActualizacion();
        when(repository.findByIdDistribuidorAndCodigoDispositivo(123, "fixture-device"))
                .thenReturn(Optional.of(entity));
        var replica = new GestoPagoTokenServiceImpl(client, repository, Mappers.getMapper(GestoPagoTokenMapper.class),
                123, "fixture-device", "fixture-password", clock, rotated, coordinator);
        assertThat(replica.obtenerTokenVigente()).isEqualTo("persisted");
        assertThat(entity.getFechaActualizacion()).isEqualTo(issuedAt);
        assertThat(rotated.needsRotation(entity.getToken())).isFalse();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-response", "blank", "success-false", "zero-expiry", "wrong-type"})
    void rechazaRespuestasInvalidasSinPersistir(String invalidity) {
        var value = response("fixture-token");
        if (invalidity.equals("null-response")) { value = null; }
        if (invalidity.equals("blank")) { value.setToken(" "); }
        if (invalidity.equals("success-false")) { value.setSuccess(false); }
        if (invalidity.equals("zero-expiry")) { value.setExpiresIn(0L); }
        if (invalidity.equals("wrong-type")) { value.setTokenType("Basic"); }
        when(client.authenticate(123, "fixture-device", "fixture-password")).thenReturn(value);
        assertThatThrownBy(service::obtenerTokenVigente).isInstanceOf(ProductIntegrationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void errorBajoDemandaNoPropagaSecretosNiCausaFeign() {
        when(client.authenticate(123, "fixture-device", "fixture-password"))
                .thenThrow(new IllegalStateException("https://fixture?password=private-secret&token=private-token"));
        assertThatThrownBy(service::obtenerTokenVigente).isInstanceOf(ProductIntegrationException.class)
                .hasMessageNotContaining("private-secret").hasMessageNotContaining("private-token")
                .hasMessageNotContaining("password=").hasNoCause();
    }

    private GestoPagoAuthResponse response(String token) {
        var result = new GestoPagoAuthResponse();
        result.setToken(token);
        result.setSuccess(true);
        return result;
    }

    private GestoPagoToken stored(String token, Instant issuedAt) {
        var result = new GestoPagoToken();
        result.setToken(cipher.encrypt(token, 123, "fixture-device"));
        result.setActivo(true);
        result.setFechaActualizacion(LocalDateTime.ofInstant(issuedAt, ZoneOffset.UTC));
        result.setExpiresIn(86400L);
        result.setTokenType("Bearer");
        return result;
    }
}
