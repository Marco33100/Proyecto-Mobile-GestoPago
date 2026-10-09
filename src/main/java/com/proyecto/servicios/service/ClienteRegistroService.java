package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;

public interface ClienteRegistroService {

    ClienteResponse registrar(RegistrarClienteRequest request);
}
