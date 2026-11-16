package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.SegundoFactorEntity;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.repositorys.sf.SegundoFactorRepository;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import java.time.Instant;

/** Invocar dentro de la transaccion que mantiene bloqueado el usuario, tambien entre replicas. */
@Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class TotpAuthorization {
    private final TotpCodes codes;
    private final TotpSecrets secrets;
    private final SegundoFactorRepository repository;
    public TotpAuthorization(TotpCodes codes, TotpSecrets secrets, SegundoFactorRepository repository) {
        this.codes = codes; this.secrets = secrets; this.repository = repository;
    }
    public void checkLock(SegundoFactorEntity factor) {
        if (factor.getBloqueadoHasta() != null) {
            if (Instant.now().isBefore(factor.getBloqueadoHasta()))
                throw new PagoException(HttpStatus.TOO_MANY_REQUESTS, "Espera antes de intentar otro codigo");
            factor.setBloqueadoHasta(null); factor.setIntentos(0);
        }
    }
    public void failed(SegundoFactorEntity factor) {
        factor.setIntentos(factor.getIntentos() + 1);
        if (factor.getIntentos() >= 5) factor.setBloqueadoHasta(Instant.now().plusSeconds(300));
        repository.saveAndFlush(factor);
    }
    /** Lanza 429 de inicio de sesion si el factor esta bloqueado por intentos fallidos. */
    public void checkLoginLock(SegundoFactorEntity factor) {
        if (factor.getBloqueadoHasta() != null && Instant.now().isBefore(factor.getBloqueadoHasta())) {
            long wait = Math.max(1, java.time.Duration.between(Instant.now(), factor.getBloqueadoHasta()).toSeconds());
            throw new com.proyecto.servicios.exception.auth.LoginRateLimitException(wait);
        }
        checkLock(factor);
    }

    /** Consume un codigo de recuperacion (un solo uso). Cuenta el intento si no coincide. */
    public boolean consumeRecovery(SegundoFactorEntity factor, String code) {
        if (factor.getRecuperacionHashes() == null) { failed(factor); return false; }
        var hashes = new java.util.ArrayList<>(java.util.Arrays.asList(factor.getRecuperacionHashes().split("\n")));
        String candidate = recoveryHash(factor.getUsuarioId(), code);
        boolean removed = hashes.removeIf(hash -> java.security.MessageDigest.isEqual(
                hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                candidate.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        if (!removed) { failed(factor); return false; }
        factor.setRecuperacionHashes(String.join("\n", hashes)); factor.setIntentos(0);
        repository.saveAndFlush(factor); return true;
    }

    /**
     * Activa el secreto pendiente ya verificado en [step]: lo guarda como definitivo,
     * genera 10 codigos de recuperacion y los devuelve en claro (solo esta vez).
     */
    public java.util.List<String> activate(SegundoFactorEntity factor, String secret, long step) {
        var recovery = new java.util.ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16]; RANDOM.nextBytes(bytes);
            String hex = java.util.HexFormat.of().withUpperCase().formatHex(bytes);
            recovery.add(String.join("-", hex.substring(0, 8), hex.substring(8, 16),
                    hex.substring(16, 24), hex.substring(24)));
        }
        java.util.UUID actor = factor.getUsuarioId();
        factor.setSecretoCifrado(secrets.encrypt(actor, secret));
        factor.setHuella(java.util.UUID.randomUUID().toString()); factor.setUltimoPaso(step);
        factor.setRecuperacionHashes(String.join("\n", recovery.stream().map(c -> recoveryHash(actor, c)).toList()));
        factor.setPendienteCifrado(null); factor.setSesionRegistro(null); factor.setVenceEn(null);
        factor.setIntentos(0); factor.setBloqueadoHasta(null); repository.saveAndFlush(factor);
        return java.util.List.copyOf(recovery);
    }

    private static String recoveryHash(java.util.UUID actor, String code) {
        return PagoCrypto.huella("totp-recovery/v1/" + actor + "/" + code);
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public boolean consume(SegundoFactorEntity factor, String code) {
        checkLock(factor);
        if (factor.getSecretoCifrado() == null) return false;
        String secret = secrets.decrypt(factor.getUsuarioId(), factor.getSecretoCifrado());
        long step = codes.matchingStep(secret, code, factor.getUltimoPaso());
        if (step < 0) { failed(factor); return false; }
        factor.setUltimoPaso(step); factor.setIntentos(0); factor.setBloqueadoHasta(null);
        // Recifrar tambien al usar una clave de almacenamiento anterior, sin cambiar la generacion del factor.
        factor.setSecretoCifrado(secrets.encrypt(factor.getUsuarioId(), secret));
        repository.saveAndFlush(factor);
        return true;
    }
}
