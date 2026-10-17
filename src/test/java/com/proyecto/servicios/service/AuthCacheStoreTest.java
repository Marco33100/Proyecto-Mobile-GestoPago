package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthCacheStoreTest {
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valores;
    private AuthCacheStore store;

    @BeforeEach
    void configurar() {
        store = new AuthCacheStore(redis, "users:", "sessions:");
    }

    @Test
    void invalidacionUsaUnSoloScriptAtomicoConComparacionDeSesion() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        store.invalidateSessionIfMatches(user, session);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.captor();
        verify(redis).execute(script.capture(), eq(List.of("sessions:" + user)), eq(session.toString()));
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('GET', KEYS[1]) == ARGV[1]", "redis.call('DEL', KEYS[1])");
        verifyNoMoreInteractions(redis);
    }

    @Test
    void falloRedisSeTraduceAExcepcionDeCache() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> store.invalidateSessionIfMatches(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AuthCacheException.class);
    }

    @Test
    void copiaLaSesionConElPrefijoYLaVigenciaRestante() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valores);

        store.replaceSession(user, session, Instant.now().plusSeconds(300));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valores).set(eq("sessions:" + user), eq(session.toString()), ttl.capture());
        assertThat(ttl.getValue()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));
        verify(redis).opsForValue();
        verifyNoMoreInteractions(redis, valores);
    }

    @Test
    void noCopiaSesionesVencidasEnRedis() {
        assertThatThrownBy(() -> store.replaceSession(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH))
                .isInstanceOf(AuthCacheException.class);
        verifyNoInteractions(redis, valores);
    }

    @Test
    void falloAlCopiarSesionSeTraduceAExcepcionDeCache() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> store.replaceSession(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(300))).isInstanceOf(AuthCacheException.class);
    }

    @Test
    void conservaLaInvalidacionConjuntaDelUsuarioYLaSesion() {
        UUID user = UUID.randomUUID();

        store.invalidateUser("uno@example.com", user);

        verify(redis).delete(List.of("users:uno@example.com", "sessions:" + user));
        verifyNoMoreInteractions(redis);
    }

    @Test
    void falloAlInvalidarUsuarioSeTraduceAExcepcionDeCache() {
        UUID user = UUID.randomUUID();
        when(redis.delete(List.of("users:uno@example.com", "sessions:" + user)))
                .thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> store.invalidateUser("uno@example.com", user))
                .isInstanceOf(AuthCacheException.class);
    }
}
