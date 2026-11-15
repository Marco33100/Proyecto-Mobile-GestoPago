package com.proyecto.servicios.model.product;

public record ProductSyncResult(
        boolean updated,
        long previousCount,
        int receivedCount
) {
}
