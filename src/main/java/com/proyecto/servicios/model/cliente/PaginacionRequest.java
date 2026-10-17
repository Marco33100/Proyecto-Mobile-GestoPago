package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import static com.proyecto.servicios.support.PaginacionDatos.*;

@Getter
@Setter
public class PaginacionRequest implements SolicitudJson {
    @Min(PAGINA_MINIMA) private int pagina = 0;
    @Min(TAMANIO_MINIMO) @Max(TAMANIO_MAXIMO) private int tamanio = 20;
}
