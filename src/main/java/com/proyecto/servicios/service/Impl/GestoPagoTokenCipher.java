package com.proyecto.servicios.service.Impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Cifrado de almacenamiento, independiente del AES exigido por el proveedor. */
@Component
@ConditionalOnProperty(name = {"app.database.enabled", "gestopago.auth.enabled"}, havingValue = "true")
public class GestoPagoTokenCipher {
    private static final String PREFIX = "gp-token:v1:";
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;
    private final byte[] previousKey;
    private final String keyId;

    public GestoPagoTokenCipher(
            @Value("${gestopago.auth.storage-key:}") String encodedKey,
            @Value("${gestopago.auth.previous-storage-key:}") String encodedPreviousKey) {
        key = decodeKey(encodedKey);
        previousKey = encodedPreviousKey == null || encodedPreviousKey.isBlank()
                ? null : decodeKey(encodedPreviousKey);
        keyId = id(key);
    }

    public String encrypt(String token, int distributor, String device) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, distributor, device);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + keyId + ":" + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception ignored) {
            throw failure();
        }
    }

    public String decrypt(String stored, int distributor, String device) {
        try {
            if (stored == null || !stored.startsWith(PREFIX)) { throw failure(); }
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            byte[] selected = parts[0].equals(keyId) ? key
                    : previousKey != null && parts[0].equals(id(previousKey)) ? previousKey : null;
            if (selected == null || parts.length != 2) { throw failure(); }
            byte[] bytes = Base64.getDecoder().decode(parts[1]);
            if (bytes.length < 29) { throw failure(); }
            byte[] nonce = java.util.Arrays.copyOfRange(bytes, 0, 12);
            return new String(cipher(Cipher.DECRYPT_MODE, selected, nonce, distributor, device)
                    .doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            throw failure();
        }
    }

    public boolean needsRotation(String stored) {
        return !stored.startsWith(PREFIX + keyId + ":");
    }

    private Cipher cipher(int mode, byte[] secret, byte[] nonce, int distributor, String device) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("gestopago-token/v1/" + distributor + "/" + device).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static byte[] decodeKey(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 32) { throw failure(); }
            return decoded;
        } catch (Exception ignored) {
            throw new IllegalStateException("Configurar GESTOPAGO_TOKEN_STORAGE_KEY y, al rotar, "
                    + "GESTOPAGO_TOKEN_PREVIOUS_STORAGE_KEY con claves Base64 de 32 bytes");
        }
    }

    private static String id(byte[] secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret), 0, 8);
        } catch (Exception ignored) { throw failure(); }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("No fue posible proteger el token del proveedor");
    }
}
