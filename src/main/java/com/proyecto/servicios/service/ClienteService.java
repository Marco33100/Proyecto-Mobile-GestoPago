package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.ActualizarClienteRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.ConsultaClientesRequest;


public interface ClienteService {

    PaginaResponse<ClienteResponse> consultar(ConsultaClientesRequest request);

    ClienteResponse actualizar(Long id, ActualizarClienteRequest request);

    ClienteResponse desactivar(Long id);
}
