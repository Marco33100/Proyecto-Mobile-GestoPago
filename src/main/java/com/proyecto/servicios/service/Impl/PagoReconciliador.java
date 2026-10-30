package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.config.PagoProperties;
import com.proyecto.servicios.service.PagoGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class PagoReconciliador {
    private final PagoLedger ledger;
    private final PagoGateway gateway;
    private final PagoProperties properties;
    public PagoReconciliador(PagoLedger ledger, PagoGateway gateway, PagoProperties properties) {
        this.ledger = ledger; this.gateway = gateway; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${gestopago.pagos.reconciliacion-delay:15000}", initialDelayString = "${gestopago.pagos.reconciliacion-delay:15000}")
    public void reconciliar() {
        if (!properties.isEnabled()) return;
        try {
            for (var id : ledger.pendientes()) {
                try {
                    var confirmacion = ledger.reclamarConfirmacion(id);
                    if (confirmacion == null) continue;
                    PagoGateway.Respuesta resultado;
                    try { resultado = gateway.confirmar(confirmacion.json()); }
                    catch (Exception e) { resultado = PagoGateway.Respuesta.incierta(); }
                    ledger.resolverConfirmacion(confirmacion, resultado);
                } catch (Exception e) { log.warn("Confirmacion de pago pendiente: {}", e.getClass().getSimpleName()); }
            }
        } catch (Exception e) { log.warn("Conciliacion temporalmente no disponible: {}", e.getClass().getSimpleName()); }
    }
}
