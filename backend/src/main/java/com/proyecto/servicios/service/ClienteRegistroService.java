package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;

public interface ClienteRegistroService {

    ClienteResponse registrar(RegistrarClienteRequest request);

    /** Registro publico: la propia persona crea su cliente, cuenta y acceso. */
    ClienteResponse registrarPropio(RegistrarClienteRequest request);
}
