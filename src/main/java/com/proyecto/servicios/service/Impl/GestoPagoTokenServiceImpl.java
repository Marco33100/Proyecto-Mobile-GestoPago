package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoAuthClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.mapper.GestoPagoTokenMapper;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoTokenRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@ConditionalOnProperty(name = {"app.database.enabled", "gestopago.auth.enabled"}, havingValue = "true")
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {
    private static final Duration PROVIDER_LIFETIME = Duration.ofHours(24);
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);
    private final GestoPagoAuthClient client;
    private final GestoPagoTokenRepository repository;
    private final GestoPagoTokenMapper mapper;
    private final Integer idDistribuidor;
    private final String codigoDispositivo;
    private final String password;
    private final Clock clock;
    // Acceso bajo el monitor: una sola autenticacion por instancia ante consultas concurrentes.
    private String cachedToken;
    private Instant usableUntil = Instant.MIN;
    private boolean storedTokenLoaded;

    @Autowired
    public GestoPagoTokenServiceImpl(GestoPagoAuthClient client, GestoPagoTokenRepository repository,
                                    GestoPagoTokenMapper mapper,
                                    @Value("${gestopago.auth.id-distribuidor}") Integer idDistribuidor,
                                    @Value("${gestopago.auth.codigo-dispositivo}") String codigoDispositivo,
                                    @Value("${gestopago.auth.password}") String password) {
        this(client, repository, mapper, idDistribuidor, codigoDispositivo, password, Clock.systemDefaultZone());
    }

    public GestoPagoTokenServiceImpl(GestoPagoAuthClient client, GestoPagoTokenRepository repository,
                                    GestoPagoTokenMapper mapper, Integer idDistribuidor,
                                    String codigoDispositivo, String password, Clock clock) {
        if (idDistribuidor == null || idDistribuidor <= 0 || !StringUtils.hasText(codigoDispositivo)
                || !StringUtils.hasText(password)) {
            throw new IllegalStateException("Configurar GESTOPAGO_AUTH_ID_DISTRIBUIDOR, "
                    + "GESTOPAGO_AUTH_CODIGO_DISPOSITIVO y GESTOPAGO_AUTH_PASSWORD");
        }
        this.client = client;
        this.repository = repository;
        this.mapper = mapper;
        this.idDistribuidor = idDistribuidor;
        this.codigoDispositivo = codigoDispositivo;
        this.password = password;
        this.clock = clock;
    }

    @Override
    public synchronized String obtenerTokenVigente() {
        try {
            if (!storedTokenLoaded) {
                loadStoredToken();
                storedTokenLoaded = true;
            }
            return isUsable() ? cachedToken : authenticate();
        } catch (Exception exception) {
            throw safeFailure(exception);
        }
    }

    @Override
    public synchronized String renovarTokenRechazado(String tokenRechazado) {
        // Otra consulta puede haber renovado ya el token utilizado por esta solicitud.
        if (isUsable() && !cachedToken.equals(tokenRechazado)) {
            return cachedToken;
        }
        storedTokenLoaded = true;
        cachedToken = null;
        usableUntil = Instant.MIN;
        try {
            return authenticate();
        } catch (Exception exception) {
            throw safeFailure(exception);
        }
    }

    @Override
    public synchronized void renovarToken() {
        // Operacion administrativa existente; no hay cron horario que incumpla el contrato diario.
        try {
            authenticate();
            storedTokenLoaded = true;
        } catch (Exception exception) {
            safeFailure(exception);
        }
    }

    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer idDistribuidor, String codigoDispositivo) {
        return repository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .filter(token -> Boolean.TRUE.equals(token.getActivo()));
    }

    private boolean isUsable() {
        return cachedToken != null && clock.instant().isBefore(usableUntil);
    }

    private void loadStoredToken() {
        obtenerTokenActivo(idDistribuidor, codigoDispositivo).filter(token ->
                StringUtils.hasText(token.getToken()) && token.getFechaActualizacion() != null
                        && token.getExpiresIn() != null && token.getExpiresIn() > 0
                        && (token.getTokenType() == null || "Bearer".equalsIgnoreCase(token.getTokenType())))
                .ifPresent(token -> {
                    Instant issuedAt = token.getFechaActualizacion().atZone(clock.getZone()).toInstant();
                    if (!issuedAt.isAfter(clock.instant())) {
                        cache(token.getToken(), issuedAt, token.getExpiresIn());
                    }
                });
    }

    private String authenticate() {
        Instant issuedAt = clock.instant();
        GestoPagoAuthResponse response = client.authenticate(idDistribuidor, codigoDispositivo, password);
        if (response == null || Boolean.FALSE.equals(response.getSuccess())
                || !StringUtils.hasText(response.getToken())
                || (response.getExpiresIn() != null && response.getExpiresIn() <= 0)
                || (StringUtils.hasText(response.getTokenType())
                    && !"Bearer".equalsIgnoreCase(response.getTokenType()))) {
            throw new IllegalStateException("Respuesta de autenticacion Gestopago no valida");
        }
        long lifetime = response.getExpiresIn() == null ? PROVIDER_LIFETIME.toSeconds()
                : Math.min(response.getExpiresIn(), PROVIDER_LIFETIME.toSeconds());
        GestoPagoToken entity = repository.findByIdDistribuidorAndCodigoDispositivo(idDistribuidor, codigoDispositivo)
                .map(existing -> {
                    mapper.updateEntity(response, existing);
                    return existing;
                }).orElseGet(() -> mapper.toEntity(response));
        entity.setIdDistribuidor(idDistribuidor);
        entity.setCodigoDispositivo(codigoDispositivo);
        entity.setActivo(true);
        entity.setTokenType("Bearer");
        // No conservar una caducidad vieja si la respuesta actual la omite.
        entity.setExpiresIn(lifetime);
        repository.save(entity);
        cache(response.getToken(), issuedAt, lifetime);
        log.info("Token Gestopago renovado correctamente");
        return cachedToken;
    }

    private void cache(String token, Instant issuedAt, long lifetimeSeconds) {
        long lifetime = Math.min(lifetimeSeconds, PROVIDER_LIFETIME.toSeconds());
        Duration margin = EXPIRY_MARGIN.compareTo(Duration.ofSeconds(lifetime).dividedBy(10)) < 0
                ? EXPIRY_MARGIN : Duration.ofSeconds(lifetime).dividedBy(10);
        cachedToken = token.trim();
        usableUntil = issuedAt.plusSeconds(lifetime).minus(margin);
    }

    private ProductIntegrationException safeFailure(Exception exception) {
        // Feign puede incluir el password de Params en su URL: nunca adjuntar mensaje ni causa.
        log.error("No fue posible obtener el token Gestopago: tipo={}", exception.getClass().getSimpleName());
        return new ProductIntegrationException(ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                "No fue posible autenticar con Gestopago; revisar las credenciales del proveedor");
    }
}
