package com.proyecto.servicios.service.clientes;

import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;

public interface ClienteRegistroService {

    ClienteResponse registrar(RegistrarClienteRequest request);
}
