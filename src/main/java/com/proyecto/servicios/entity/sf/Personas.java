package com.proyecto.servicios.entity.sf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Table(name="personas")
@Entity
@Getter
@Setter
public class Personas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer Id;
    @Column(name="nombre")
    private String nombre;
    @Column(name="apellido_paterno")
    private String apellidoP;
    @Column(name="apellido_materno")
   private String apellidoMaterno;
}
