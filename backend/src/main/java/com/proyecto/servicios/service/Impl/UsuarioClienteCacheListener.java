package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.exception.auth.AuthCacheException;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
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
            cacheStore.invalidateUser(event.correoAnterior());
            if (!event.correoAnterior().equals(event.correoActual())) {
                cacheStore.invalidateUser(event.correoActual());
            }
        } catch (AuthCacheException exception) {
            log.error("No fue posible invalidar el acceso del cliente en Redis: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
