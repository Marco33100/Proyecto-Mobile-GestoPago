package com.proyecto.servicios.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gestopago.pagos")
public class PagoProperties {
    // Fail closed: enable only after configuring the provider's keys and confirming the timezone.
    private boolean enabled;
    private String unidad = "083";
    private String zonaHoraria = "America/Mexico_City";
    private Duration autorizacionTtl = Duration.ofMinutes(5);
    private Duration confirmacionIntervalo = Duration.ofMinutes(1);
    // Limites opcionales: se aplican si se configuran para un producto.
    private Map<Integer, LimiteMonto> limitesPorProducto = new HashMap<>();
    public record LimiteMonto(Integer idServicio, BigDecimal minimo, BigDecimal maximo) { }
    // Sin regla configurada, tipoFront 4 admite importes positivos hasta la deuda consultada.
    private Map<Integer, ReglaReferencia> reglasReferenciaPorProducto = new HashMap<>();
    public enum ModalidadReferencia { IMPORTE_EXACTO, PARCIAL_HASTA_SALDO }
    public record ReglaReferencia(Integer idServicio, ModalidadReferencia modalidad) { }
    // Limites propios del negocio, opcionales: no representan condiciones del proveedor.
    private BigDecimal maximoPorOperacion;
    private BigDecimal maximoDiarioPorCliente;
    private Conciliacion conciliacion = new Conciliacion();
    @Getter @Setter
    public static class Conciliacion {
        private boolean enabled;
        private Set<UUID> operadores = Set.of();
        private Duration propuestaTtl = Duration.ofHours(1);
    }
}
