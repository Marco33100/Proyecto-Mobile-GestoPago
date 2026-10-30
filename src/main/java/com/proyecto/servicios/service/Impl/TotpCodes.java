package com.proyecto.servicios.service.Impl;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;

/** RFC 6238: HMAC-SHA1, seis digitos, periodos de 30 segundos. Sin red ni almacenamiento. */
@Component
public class TotpCodes {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public TotpCodes() { this(Clock.systemUTC()); }
    public TotpCodes(Clock clock) { this.clock = clock; }

    public String newSecret() {
        byte[] bytes = new byte[20]; random.nextBytes(bytes);
        StringBuilder encoded = new StringBuilder(32);
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255); bits += 8;
            while (bits >= 5) { bits -= 5; encoded.append(BASE32[(buffer >>> bits) & 31]); }
        }
        return encoded.toString();
    }

    public long currentStep() { return clock.instant().getEpochSecond() / 30; }

    public long matchingStep(String secret, String code, long lastUsed) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long now = currentStep();
        for (long step : new long[]{now, now - 1, now + 1}) {
            if (step > lastUsed && step >= 0 && MessageDigest.isEqual(
                    code.getBytes(StandardCharsets.US_ASCII), code(secret, step).getBytes(StandardCharsets.US_ASCII))) {
                return step;
            }
        }
        return -1;
    }

    public String code(String secret, long step) {
        return hotp(secret, step, 6);
    }

    // Ocho digitos permite comprobar directamente los vectores oficiales RFC 6238.
    public String hotp(String secret, long counter, int digits) {
        try {
            byte[] decoded = decode(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decoded, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            if (digits != 6 && digits != 8) throw new IllegalArgumentException();
            return String.format(Locale.ROOT, "%0" + digits + "d", binary % (digits == 6 ? 1_000_000 : 100_000_000));
        } catch (Exception ignored) {
            throw new IllegalStateException("No fue posible verificar el segundo factor");
        }
    }

    private byte[] decode(String secret) {
        if (secret == null || !secret.matches("[A-Z2-7]{32}")) throw new IllegalArgumentException();
        byte[] bytes = new byte[20]; int buffer = 0, bits = 0, index = 0;
        for (char value : secret.toCharArray()) {
            int n = value >= 'A' && value <= 'Z' ? value - 'A' : value - '2' + 26;
            buffer = (buffer << 5) | n; bits += 5;
            if (bits >= 8) { bits -= 8; bytes[index++] = (byte) (buffer >>> bits); }
        }
        return bytes;
    }
}
