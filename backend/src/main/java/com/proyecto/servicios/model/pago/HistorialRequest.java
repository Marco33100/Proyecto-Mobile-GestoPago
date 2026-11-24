package com.proyecto.servicios.model.pago;

import com.proyecto.servicios.entity.sf.PagoEntity.Estado;
import com.proyecto.servicios.model.cliente.PaginacionRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Paginacion del historial con filtro por resultado; sin filtro, solo compras exitosas. */
@Getter
@Setter
public class HistorialRequest extends PaginacionRequest {

    /** Solo operaciones autorizadas: las preparadas o vencidas nunca se enviaron. */
    public enum Filtro {
        TODOS(List.of(Estado.APLICADO, Estado.RECHAZADO, Estado.ENVIADO, Estado.PENDIENTE, Estado.REVISION)),
        EXITOSAS(List.of(Estado.APLICADO)),
        RECHAZADAS(List.of(Estado.RECHAZADO)),
        PENDIENTES(List.of(Estado.ENVIADO, Estado.PENDIENTE, Estado.REVISION));

        private final List<Estado> estados;

        Filtro(List<Estado> estados) {
            this.estados = estados;
        }

        public List<Estado> estados() {
            return estados;
        }
    }

    @NotNull private Filtro filtro = Filtro.EXITOSAS;
}
