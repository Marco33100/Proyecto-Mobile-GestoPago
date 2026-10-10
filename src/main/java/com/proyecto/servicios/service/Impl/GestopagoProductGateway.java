package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.ProductCatalogClient;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.GestoPagoTokenRejectedException;
import com.proyecto.servicios.model.product.ProductListResponse;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
public class GestopagoProductGateway {

    private static final String SUCCESS_CODE = "01";

    private final ProductCatalogClient productCatalogClient;
    private final GestoPagoAccessTokenProvider tokenProvider;
    private final long failureCooldownNanos;
    // Publicar un unico estado evita mezclar el codigo, mensaje y plazo de fallos concurrentes.
    private volatile FailureCooldown failureCooldown;

    @Autowired
    public GestopagoProductGateway(
            ProductCatalogClient productCatalogClient,
            GestoPagoAccessTokenProvider tokenProvider,
            @Value("${product.service.failure-cooldown}") Duration failureCooldown
    ) {
        this.productCatalogClient = productCatalogClient;
        this.tokenProvider = tokenProvider;
        this.failureCooldownNanos = failureCooldown.toNanos();
    }

    public ProductListResponse fetchCatalog() {
        rejectDuringCooldown();
        log.info("Inicia invocación al catálogo de productos Gestopago");
        try {
            ResponseEntity<ProductListResponse> response = fetchWithTokenRecovery();
            validateResponse(response);
            clearFailure();
            return response.getBody();
        } catch (ProductIntegrationException exception) {
            registerFailure(exception);
            log.error("Error controlado al consultar Gestopago: codigo={}",
                    exception.getErrorType().getCode());
            throw exception;
        } catch (RetryableException exception) {
            ProductIntegrationException mapped = connectionError(exception);
            registerFailure(mapped);
            throw mapped;
        } catch (DecodeException exception) {
            log.error("Gestopago respondió, pero su XML no pudo deserializarse");
            ProductIntegrationException mapped = new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero los productos no pudieron serializarse",
                    exception
            );
            registerFailure(mapped);
            throw mapped;
        } catch (FeignException exception) {
            log.error("Gestopago respondió con un estado HTTP no exitoso: status={}", exception.status());
            ProductIntegrationException mapped = new ProductIntegrationException(
                    ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "El servicio externo de Gestopago no está disponible o rechazó la solicitud",
                    exception
            );
            registerFailure(mapped);
            throw mapped;
        } catch (Exception exception) {
            log.error("Error inesperado al consultar Gestopago: tipo={}", exception.getClass().getSimpleName());
            ProductIntegrationException mapped = new ProductIntegrationException(
                    ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "El servicio externo de Gestopago no funciona o está caído",
                    exception
            );
            registerFailure(mapped);
            throw mapped;
        } finally {
            log.info("Finaliza invocación al catálogo de productos Gestopago");
        }
    }

    private ResponseEntity<ProductListResponse> fetchWithTokenRecovery() {
        String token = tokenProvider.obtenerToken();
        try {
            return productCatalogClient.getProductList("Bearer " + token);
        } catch (GestoPagoTokenRejectedException rejected) {
            String renewed = tokenProvider.renovarTrasRechazo(token);
            // GET de solo lectura; nunca un bucle ni reintentos de pagos.
            return productCatalogClient.getProductList("Bearer " + renewed);
        }
    }

    private void validateResponse(ResponseEntity<ProductListResponse> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "Gestopago respondió con un estado HTTP no exitoso"
            );
        }

        ProductListResponse body = response.getBody();
        if (body == null || body.getMensaje() == null || body.getProductos() == null) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero los productos no pudieron serializarse"
            );
        }

        if (!SUCCESS_CODE.equals(body.getMensaje().getCodigo())) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                    "Gestopago devolvió un código de negocio no exitoso"
            );
        }
    }

    private ProductIntegrationException connectionError(RetryableException exception) {
        Throwable cause = exception.getCause();
        boolean connectionOrTimeout = cause instanceof SocketTimeoutException
                || cause instanceof ConnectException
                || containsConnectionMessage(exception.getMessage());

        String message = connectionOrTimeout
                ? "Error de conexión a internet o timeout al comunicarse con Gestopago"
                : "No fue posible establecer comunicación con Gestopago";
        log.error("Falla de red al consultar Gestopago");
        return new ProductIntegrationException(
                ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                message,
                exception
        );
    }

    private boolean containsConnectionMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("connection")
                || normalized.contains("refused");
    }

    private void rejectDuringCooldown() {
        FailureCooldown currentFailure = failureCooldown;
        if (currentFailure != null && System.nanoTime() < currentFailure.retryAfterNanos()) {
            throw new ProductIntegrationException(currentFailure.errorType(), currentFailure.message());
        }
    }

    private void registerFailure(ProductIntegrationException exception) {
        failureCooldown = new FailureCooldown(exception.getErrorType(), exception.getMessage(),
                System.nanoTime() + failureCooldownNanos);
    }

    private void clearFailure() {
        failureCooldown = null;
    }

    private record FailureCooldown(ProductIntegrationErrorType errorType, String message, long retryAfterNanos) {
    }
}
