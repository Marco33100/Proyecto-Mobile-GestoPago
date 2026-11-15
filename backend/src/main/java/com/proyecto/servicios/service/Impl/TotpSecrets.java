package com.proyecto.servicios.service.Impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Misma clave externa de almacenamiento, con AAD exclusivo del segundo factor y usuario. */
@Component
public class TotpSecrets {
    private final GestoPagoTokenCipher cipher;
    public TotpSecrets(@Value("${gestopago.auth.storage-key:}") String key,
                       @Value("${gestopago.auth.previous-storage-key:}") String previousKey) {
        cipher = key == null || key.isBlank() ? null : new GestoPagoTokenCipher(key, previousKey);
    }
    public void requireConfigured() {
        if (cipher == null) throw new IllegalStateException("Configurar GESTOPAGO_TOKEN_STORAGE_KEY para el segundo factor");
    }
    public String encrypt(UUID actor, String value) {
        requireConfigured(); return cipher.encrypt(value, 0, "payment-totp/v1/" + actor);
    }
    public String decrypt(UUID actor, String value) {
        requireConfigured(); return cipher.decrypt(value, 0, "payment-totp/v1/" + actor);
    }
}
