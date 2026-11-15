package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.model.auth.SesionRevocadaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class UsuarioClienteCacheListener {
    private final AuthCacheStore cacheStore;

    @Autowired
    public UsuarioClienteCacheListener(AuthCacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    // Solo se invalida Redis despues del commit, nunca si PostgreSQL hizo rollback.
    @TransactionalEventListener
    public void invalidarAcceso(UsuarioClienteModificadoEvent event) {
        try {
            cacheStore.invalidateUser(event.correoAnterior(), event.usuarioId());
            if (!event.correoAnterior().equals(event.correoActual())) {
                cacheStore.invalidateUser(event.correoActual(), event.usuarioId());
            }
        } catch (AuthCacheException exception) {
            log.error("No fue posible invalidar el acceso del cliente en Redis: {}",
                    exception.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener
    public void invalidarSesionRevocada(SesionRevocadaEvent event) {
        try {
            cacheStore.invalidateSessionIfMatches(event.usuarioId(), event.sesionId());
        } catch (AuthCacheException exception) {
            // PostgreSQL ya revoco el token. Una falla de Redis no vuelve a autorizarlo.
            log.error("No fue posible invalidar la sesion revocada en Redis: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
