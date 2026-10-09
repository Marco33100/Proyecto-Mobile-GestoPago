package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.model.auth.SesionRevocadaEvent;
import com.proyecto.servicios.service.Impl.AuthCacheStore;
import com.proyecto.servicios.service.Impl.UsuarioClienteCacheListener;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatCode;

class UsuarioClienteCacheListenerTest {
    @Test
    void logoutComparaSesionAntesDeEliminarYRedisNoImpideRevocacion() {
        AuthCacheStore cache = mock(AuthCacheStore.class);
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        doThrow(new AuthCacheException("offline", null)).when(cache).invalidateSessionIfMatches(user, session);
        assertThatCode(() -> new UsuarioClienteCacheListener(cache)
                .invalidarSesionRevocada(new SesionRevocadaEvent(user, session))).doesNotThrowAnyException();
        verify(cache).invalidateSessionIfMatches(user, session);
        verifyNoMoreInteractions(cache);
    }
    @Test
    void invalidaCorreoAnteriorActualYSesion() {
        AuthCacheStore cache = mock(AuthCacheStore.class);
        UUID id = UUID.randomUUID();
        new UsuarioClienteCacheListener(cache).invalidarAcceso(
                new UsuarioClienteModificadoEvent(id, "antes@example.com", "nuevo@example.com"));
        verify(cache).invalidateUser("antes@example.com", id);
        verify(cache).invalidateUser("nuevo@example.com", id);
    }

    @Test
    void falloRedisNoConvierteCommitExitosoEnError() {
        AuthCacheStore cache = mock(AuthCacheStore.class);
        UUID id = UUID.randomUUID();
        doThrow(new AuthCacheException("fallo", null)).when(cache).invalidateUser("uno@example.com", id);
        assertThatCode(() -> new UsuarioClienteCacheListener(cache).invalidarAcceso(
                new UsuarioClienteModificadoEvent(id, "uno@example.com", "uno@example.com")))
                .doesNotThrowAnyException();
    }
}
