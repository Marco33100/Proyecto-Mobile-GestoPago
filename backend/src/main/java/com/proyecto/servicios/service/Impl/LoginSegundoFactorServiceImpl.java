package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.PlataformaSesion;
import com.proyecto.servicios.entity.sf.SegundoFactorEntity;
import com.proyecto.servicios.exception.auth.SesionActivaException;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.CodigoSegundoFactorInvalidoException;
import com.proyecto.servicios.exception.auth.SegundoFactorNoDisponibleException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.model.auth.LoginDesafioResponse;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.VerificarLoginRequest;
import com.proyecto.servicios.repositorys.sf.SegundoFactorRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtTokenService;
import com.proyecto.servicios.service.LoginSegundoFactorService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Inicio de sesion con doble factor para clientes y ejecutivos.
 *
 * <p>Reutiliza el autenticador TOTP de pagos (un solo autenticador por usuario): mismos
 * secretos cifrados, codigos de recuperacion, limite de 5 intentos y bloqueo de 5 minutos.
 * Un usuario sin autenticador lo vincula durante el inicio de sesion; nadie entra sin el.
 */
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class LoginSegundoFactorServiceImpl implements LoginSegundoFactorService {
    static final Duration VIGENCIA_DESAFIO = Duration.ofMinutes(5);

    private final AuthService authService;
    private final UserRepository users;
    private final SegundoFactorRepository factors;
    private final TotpCodes codes;
    private final TotpSecrets secrets;
    private final TotpAuthorization authorization;
    private final JwtTokenService jwt;
    private final TransactionTemplate tx;

    @Autowired
    public LoginSegundoFactorServiceImpl(AuthService authService, UserRepository users,
                                         SegundoFactorRepository factors, TotpCodes codes, TotpSecrets secrets,
                                         TotpAuthorization authorization, JwtTokenService jwt,
                                         @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        this.authService = authService;
        this.users = users;
        this.factors = factors;
        this.codes = codes;
        this.secrets = secrets;
        this.authorization = authorization;
        this.jwt = jwt;
        this.tx = new TransactionTemplate(manager);
    }

    @Override
    public LoginDesafioResponse iniciar(LoginRequest request) {
        requireSecrets();
        UserEntity user = authService.verificarCredenciales(request);
        UUID challengeId = UUID.randomUUID();
        try {
            LoginDesafioResponse.Configuracion configuracion = tx.execute(status -> {
                SegundoFactorEntity factor = factors.findById(user.getId()).orElseGet(SegundoFactorEntity::new);
                factor.setUsuarioId(user.getId());
                if (factor.getSecretoCifrado() != null) {
                    return null;
                }
                // Sin autenticador: secreto pendiente ligado a este desafio y con la misma vigencia.
                String secret = codes.newSecret();
                factor.setPendienteCifrado(secrets.encrypt(user.getId(), secret));
                factor.setSesionRegistro(challengeId);
                factor.setVenceEn(Instant.now().plus(VIGENCIA_DESAFIO));
                factors.saveAndFlush(factor);
                String label = URLEncoder.encode("Gestopago:" + user.getEmail(), StandardCharsets.UTF_8)
                        .replace("+", "%20");
                return new LoginDesafioResponse.Configuracion(secret, "otpauth://totp/" + label
                        + "?secret=" + secret + "&issuer=Gestopago&algorithm=SHA1&digits=6&period=30");
            });
            JwtTokenService.TokenResult desafio =
                    jwt.generateLoginChallenge(user.getId(), challengeId, VIGENCIA_DESAFIO);
            return new LoginDesafioResponse(desafio.value(), configuracion == null, desafio.expiresAt(),
                    configuracion);
        } catch (DataAccessException exception) {
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }

    @Override
    public LoginResponse verificar(VerificarLoginRequest request, PlataformaSesion plataforma) {
        requireSecrets();
        JwtTokenService.LoginChallenge challenge;
        try {
            challenge = jwt.validateLoginChallenge(request.desafio());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new CodigoSegundoFactorInvalidoException();
        }
        boolean conCodigo = request.codigo() != null;
        boolean conRecuperacion = request.codigoRecuperacion() != null;
        if (conCodigo == conRecuperacion) {
            // Exactamente un metodo de verificacion.
            throw new CodigoSegundoFactorInvalidoException();
        }
        try {
            // Los fallos se devuelven fuera de la transaccion para conservar intentos y bloqueos.
            Resultado resultado = tx.execute(status -> verificarEnTransaccion(challenge, request));
            if (resultado == null) {
                throw new CodigoSegundoFactorInvalidoException();
            }
            LoginResponse sesion = authService.emitirSesion(resultado.user(), plataforma);
            return resultado.codigosRecuperacion() == null
                    ? sesion : sesion.conCodigosRecuperacion(resultado.codigosRecuperacion());
        } catch (DataAccessException exception) {
            throw new AuthPersistenceException("El servicio de autenticacion no esta disponible", exception);
        }
    }

    private Resultado verificarEnTransaccion(JwtTokenService.LoginChallenge challenge, VerificarLoginRequest request) {
        // El bloqueo del usuario serializa intentos concurrentes, tambien entre replicas.
        UserEntity user = users.bloquearUsuarioDeSesion(challenge.userId()).orElse(null);
        if (user == null) {
            return null;
        }
        if (!user.isEnabled() || (user.getCliente() != null && !user.getCliente().isActivo())) {
            throw new UsuarioInactivoException();
        }
        SegundoFactorEntity factor = factors.findById(user.getId()).orElse(null);
        if (factor == null) {
            return null;
        }
        authorization.checkLoginLock(factor);

        if (factor.getSecretoCifrado() != null) {
            if (authorization.accepts(factor, request.codigo(), request.codigoRecuperacion())) {
                exigirConfirmacionSiHaySesion(user, request);
            }
            boolean ok = request.codigoRecuperacion() != null
                    ? authorization.consumeRecovery(factor, request.codigoRecuperacion().toUpperCase(Locale.ROOT))
                    : authorization.consume(factor, request.codigo());
            return ok ? new Resultado(user, null) : null;
        }

        // Vinculacion: el secreto pendiente debe pertenecer a este desafio y seguir vigente.
        if (request.codigo() == null || factor.getPendienteCifrado() == null
                || !challenge.challengeId().equals(factor.getSesionRegistro())
                || factor.getVenceEn() == null || !Instant.now().isBefore(factor.getVenceEn())) {
            return null;
        }
        String secret = secrets.decrypt(user.getId(), factor.getPendienteCifrado());
        long step = codes.matchingStep(secret, request.codigo(), -1);
        if (step < 0) {
            authorization.failed(factor);
            return null;
        }
        exigirConfirmacionSiHaySesion(user, request);
        return new Resultado(user, authorization.activate(factor, secret, step));
    }

    /**
     * Con un codigo correcto y otra sesion abierta, pide confirmacion antes de reemplazarla.
     * Se lanza antes de consumir el codigo, asi que el mismo codigo sirve al confirmar.
     */
    private void exigirConfirmacionSiHaySesion(UserEntity user, VerificarLoginRequest request) {
        if (request.confirmaCerrarSesionAnterior()) {
            return;
        }
        authService.sesionActiva(user.getId()).ifPresent(sesion -> {
            throw new SesionActivaException(sesion);
        });
    }

    private void requireSecrets() {
        try {
            secrets.requireConfigured();
        } catch (IllegalStateException exception) {
            throw new SegundoFactorNoDisponibleException();
        }
    }

    private record Resultado(UserEntity user, List<String> codigosRecuperacion) {
    }
}
