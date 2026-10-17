package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.model.SolicitudJson;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import static com.proyecto.servicios.support.PaginacionDatos.*;

/** Parametros de entrada; las reglas de busqueda y permisos se aplican en el servicio. */
@Getter
@Setter
public class ConsultaClientesRequest implements SolicitudJson {
    @Min(PAGINA_MINIMA) private int pagina = 0;
    @Min(TAMANIO_MINIMO) @Max(TAMANIO_MAXIMO) private int tamanio = 20;
    @Size(max = 50) private String nombre;
    @Size(max = 13) private String rfc;
    @Size(max = 18) private String curp;
    @Positive(message = "El ID debe ser mayor que cero") private Long id;
    @Email(message = "El correo no tiene un formato valido")
    @Pattern(regexp = ".*\\S.*", message = "El correo no puede estar vacio")
    @Size(max = 100) private String correo;
    @Pattern(regexp = com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_REGEX,
            message = com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_MENSAJE)
    private String numeroCuenta;
    private Boolean activo;
    @JsonDeserialize(using = FechaSinHoraDeserializer.class) private LocalDate desde;
    @JsonDeserialize(using = FechaSinHoraDeserializer.class) private LocalDate hasta;
}
