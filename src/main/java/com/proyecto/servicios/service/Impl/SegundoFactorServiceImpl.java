package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.SegundoFactorEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.service.SegundoFactorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service @PreAuthorize("hasRole('CLIENTE')")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class SegundoFactorServiceImpl implements SegundoFactorService {
    private final SegundoFactorRepository factors;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TotpCodes codes;
    private final TotpSecrets secrets;
    private final TotpAuthorization authorization;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();
    public SegundoFactorServiceImpl(SegundoFactorRepository factors, UserRepository users, PasswordEncoder passwords,
                                    TotpCodes codes, TotpSecrets secrets, TotpAuthorization authorization,
                                    @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        this.factors = factors; this.users = users; this.passwords = passwords; this.codes = codes;
        this.secrets = secrets; this.authorization = authorization; tx = new TransactionTemplate(manager);
    }
    private UserEntity activeUser(UUID actor) {
        var user = users.bloquearUsuarioDeSesion(actor).orElseThrow(() -> error(HttpStatus.NOT_FOUND));
        if (!user.isEnabled() || user.getCliente() == null || !user.getCliente().isActivo()) throw error(HttpStatus.FORBIDDEN);
        return user;
    }
    @Override public EstadoFactorResponse estado() {
        var actor = PagoIdentidad.actual();
        return tx.execute(status -> {
            activeUser(actor.userId());
            return new EstadoFactorResponse(factors.findById(actor.userId())
                    .map(f -> f.getSecretoCifrado() != null).orElse(false));
        });
    }
    @Override public PrepararFactorResponse preparar(PrepararFactorRequest request) {
        secrets.requireConfigured(); var actor = PagoIdentidad.actual();
        var result = tx.execute(status -> {
            var user = activeUser(actor.userId());
            var factor = factors.findById(actor.userId()).orElseGet(SegundoFactorEntity::new);
            factor.setUsuarioId(actor.userId()); authorization.checkLock(factor);
            boolean passwordOk = request.contrasena().getBytes(StandardCharsets.UTF_8).length <= 72
                    && passwords.matches(request.contrasena(), user.getPasswordHash());
            if (!passwordOk) { authorization.failed(factor); return null; }
            if (factor.getSecretoCifrado() != null) {
                if (request.codigoActual() != null && request.codigoRecuperacion() != null) {
                    authorization.failed(factor); return null;
                }
                boolean verified = request.codigoRecuperacion() != null
                        ? consumeRecovery(factor, request.codigoRecuperacion())
                        : authorization.consume(factor, request.codigoActual());
                if (!verified) return null;
            }
            String secret = codes.newSecret();
            factor.setPendienteCifrado(secrets.encrypt(actor.userId(), secret));
            factor.setSesionRegistro(actor.sessionId()); factor.setVenceEn(Instant.now().plusSeconds(300));
            factors.saveAndFlush(factor);
            String label = URLEncoder.encode("Gestopago:" + actor.email(), StandardCharsets.UTF_8).replace("+", "%20");
            return new PrepararFactorResponse(secret, "otpauth://totp/" + label
                    + "?secret=" + secret + "&issuer=Gestopago&algorithm=SHA1&digits=6&period=30", factor.getVenceEn());
        });
        // Fallos se devuelven fuera de la transaccion para conservar intentos y bloqueos.
        if (result == null) throw error(HttpStatus.FORBIDDEN);
        return result;
    }
    @Override public ActivarFactorResponse activar(ActivarFactorRequest request) {
        secrets.requireConfigured(); var actor = PagoIdentidad.actual();
        var result = tx.execute(status -> {
            activeUser(actor.userId());
            var factor = factors.findById(actor.userId()).orElse(null);
            if (factor == null) return null;
            authorization.checkLock(factor);
            if (factor.getPendienteCifrado() == null || !actor.sessionId().equals(factor.getSesionRegistro())
                    || factor.getVenceEn() == null || !Instant.now().isBefore(factor.getVenceEn())) return null;
            String secret = secrets.decrypt(actor.userId(), factor.getPendienteCifrado());
            long step = codes.matchingStep(secret, request.codigo(), -1);
            if (step < 0) { authorization.failed(factor); return null; }
            var recovery = new ArrayList<String>();
            for (int i = 0; i < 10; i++) {
                byte[] bytes = new byte[16]; random.nextBytes(bytes);
                String hex = HexFormat.of().withUpperCase().formatHex(bytes);
                recovery.add(String.join("-", hex.substring(0, 8), hex.substring(8, 16), hex.substring(16, 24), hex.substring(24)));
            }
            factor.setSecretoCifrado(secrets.encrypt(actor.userId(), secret));
            factor.setHuella(UUID.randomUUID().toString()); factor.setUltimoPaso(step);
            factor.setRecuperacionHashes(String.join("\n", recovery.stream().map(c -> recoveryHash(actor.userId(), c)).toList()));
            factor.setPendienteCifrado(null); factor.setSesionRegistro(null); factor.setVenceEn(null);
            factor.setIntentos(0); factor.setBloqueadoHasta(null); factors.saveAndFlush(factor);
            return new ActivarFactorResponse(true, List.copyOf(recovery));
        });
        if (result == null) throw error(HttpStatus.FORBIDDEN);
        return result;
    }
    private boolean consumeRecovery(SegundoFactorEntity factor, String code) {
        if (factor.getRecuperacionHashes() == null) { authorization.failed(factor); return false; }
        var hashes = new ArrayList<>(Arrays.asList(factor.getRecuperacionHashes().split("\n")));
        String candidate = recoveryHash(factor.getUsuarioId(), code);
        boolean removed = hashes.removeIf(hash -> MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII)));
        if (!removed) { authorization.failed(factor); return false; }
        factor.setRecuperacionHashes(String.join("\n", hashes)); factor.setIntentos(0);
        factors.saveAndFlush(factor); return true;
    }
    private static String recoveryHash(UUID actor, String code) { return PagoCrypto.huella("totp-recovery/v1/" + actor + "/" + code); }
    private static PagoException error(HttpStatus status) { return new PagoException(status, "No fue posible verificar el segundo factor"); }
}
