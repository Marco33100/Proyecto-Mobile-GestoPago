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
