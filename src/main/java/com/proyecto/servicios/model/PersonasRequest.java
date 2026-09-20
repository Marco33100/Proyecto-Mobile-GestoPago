package com.proyecto.servicios.model;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonasRequest {

    private String nombre;
    private String apellidoP;
    private String apellidoMaterno;

}
