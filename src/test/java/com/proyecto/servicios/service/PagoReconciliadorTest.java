package com.proyecto.servicios.service;

import com.proyecto.servicios.config.PagoProperties;
import com.proyecto.servicios.service.Impl.PagoLedger;
import com.proyecto.servicios.service.Impl.PagoReconciliador;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PagoReconciliadorTest {
    @Test void falloDeConsultaEsInciertoYNuncaReenviaLaVenta() {
        var ledger = mock(PagoLedger.class); var gateway = mock(PagoGateway.class); var properties = new PagoProperties(); properties.setEnabled(true);
        UUID id = UUID.randomUUID(); var claim = new PagoLedger.Confirmacion(id, "payload-original", UUID.randomUUID());
        when(ledger.pendientes()).thenReturn(List.of(id)); when(ledger.reclamarConfirmacion(id)).thenReturn(claim);
        when(gateway.confirmar("payload-original")).thenThrow(new IllegalStateException("no registrar detalle sensible"));
        new PagoReconciliador(ledger, gateway, properties).reconciliar();
        verify(ledger).resolverConfirmacion(claim, PagoGateway.Respuesta.incierta());
        verify(gateway, never()).enviar(any());
    }
    @Test void falloAlGuardarUnaOperacionNoImpideConciliarLaSiguiente() {
        var ledger = mock(PagoLedger.class); var gateway = mock(PagoGateway.class); var properties = new PagoProperties(); properties.setEnabled(true);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var primera = new PagoLedger.Confirmacion(a, "a", UUID.randomUUID()); var segunda = new PagoLedger.Confirmacion(b, "b", UUID.randomUUID());
        when(ledger.pendientes()).thenReturn(List.of(a, b));
        when(ledger.reclamarConfirmacion(a)).thenReturn(primera); when(ledger.reclamarConfirmacion(b)).thenReturn(segunda);
        when(gateway.confirmar(any())).thenReturn(PagoGateway.Respuesta.incierta());
        when(ledger.resolverConfirmacion(eq(primera), any())).thenThrow(new IllegalStateException("fallo simulado"));
        new PagoReconciliador(ledger, gateway, properties).reconciliar();
        verify(ledger).resolverConfirmacion(segunda, PagoGateway.Respuesta.incierta()); verify(gateway, never()).enviar(any());
    }
    @Test void deshabilitadoNoConsultaProveedorNiBase() {
        var ledger = mock(PagoLedger.class); var gateway = mock(PagoGateway.class);
        new PagoReconciliador(ledger, gateway, new PagoProperties()).reconciliar();
        verifyNoInteractions(ledger, gateway);
    }
    @Test void sinReclamoExclusivoNoConsultaProveedor() {
        var ledger = mock(PagoLedger.class); var gateway = mock(PagoGateway.class); var properties = new PagoProperties(); properties.setEnabled(true);
        UUID id = UUID.randomUUID(); when(ledger.pendientes()).thenReturn(List.of(id));
        new PagoReconciliador(ledger, gateway, properties).reconciliar(); verifyNoInteractions(gateway);
    }
}
