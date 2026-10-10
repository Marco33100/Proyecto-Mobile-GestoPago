package com.proyecto.servicios.exception;

/** Solo se produce por HTTP 401 o por el 403 EXPIRED documentado por el proveedor. */
public class GestoPagoTokenRejectedException extends ProductIntegrationException {
    public GestoPagoTokenRejectedException() {
        super(ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE,
                "Gestopago rechazo el token de autenticacion");
    }
}
