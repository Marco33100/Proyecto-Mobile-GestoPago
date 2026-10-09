package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthCacheStoreTest {
    @Test
    void invalidacionUsaUnSoloScriptAtomicoConComparacionDeSesion() {
        var redis = mock(StringRedisTemplate.class);
        var store = new AuthCacheStore(redis, new ObjectMapper(), "users:", "sessions:", Duration.ofMinutes(5));
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
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("offline"));
        var store = new AuthCacheStore(redis, new ObjectMapper(), "users:", "sessions:", Duration.ofMinutes(5));
        assertThatThrownBy(() -> store.invalidateSessionIfMatches(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AuthCacheException.class);
    }
}
