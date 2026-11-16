package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthCacheStoreTest {
    @Mock private StringRedisTemplate redis;
    private AuthCacheStore store;

    @BeforeEach
    void configurar() {
        store = new AuthCacheStore(redis, "users:");
    }

    @Test
    void invalidaElUsuarioConSuPrefijo() {
        store.invalidateUser("uno@example.com");

        verify(redis).delete("users:uno@example.com");
        verifyNoMoreInteractions(redis);
    }

    @Test
    void falloAlInvalidarUsuarioSeTraduceAExcepcionDeCache() {
        when(redis.delete("users:uno@example.com")).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> store.invalidateUser("uno@example.com"))
                .isInstanceOf(AuthCacheException.class);
    }
}
