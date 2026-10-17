package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.model.cliente.PaginacionRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Filtros opcionales combinados con AND; null significa no aplicar ese criterio. */
@Getter
@Setter
public class UsuarioBusquedaRequest extends PaginacionRequest {
    @Size(max = 203)
    @Pattern(regexp = ".*\\S.*", message = "El nombre no puede contener solo espacios.")
    private String nombre;
    @Email(message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 254)
    @Pattern(regexp = ".*\\S.*", message = "El correo electrónico no puede estar vacío.")
    private String correo;
    @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe contener exactamente 10 dígitos.")
    private String telefono;
    private Boolean activo;
}
