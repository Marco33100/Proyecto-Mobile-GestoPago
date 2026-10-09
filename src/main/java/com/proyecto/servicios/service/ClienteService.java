package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.ActualizarClienteRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import java.time.LocalDate;


public interface ClienteService {

    PaginaResponse<ClienteResponse> consultarTodos(int pagina, int tamanio);

    PaginaResponse<ClienteResponse> consultarTodos(int pagina, int tamanio, Boolean activo,
                                                  LocalDate desde, LocalDate hasta);

    ClienteResponse consultarPorCorreo(String correo);

    ClienteResponse consultarPorId(Long id);

    ClienteResponse consultarPorCurp(String curp);

    ClienteResponse consultarPorRfc(String rfc);

    ClienteResponse consultarPorNumeroCuenta(String numeroCuenta);

    ClienteResponse actualizar(Long id, ActualizarClienteRequest request);

    ClienteResponse desactivar(Long id);
}
