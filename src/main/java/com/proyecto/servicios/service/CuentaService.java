package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;

public interface CuentaService {

    CuentaEntity crearPara(ClienteEntity cliente);

    CuentaResponse consultarPorNumeroCuenta(String numeroCuenta);

    CuentaResponse desactivar(String numeroCuenta);

    CuentaResponse actualizarEstado(String numeroCuenta, ActualizarEstadoCuentaRequest request);

    SaldoCuentaResponse consultarSaldo(String numeroCuenta);

    PaginaResponse<CuentaResponse> consultarPropias(int pagina, int tamanio);

    PaginaResponse<CuentaResponse> consultarActivas(int pagina, int tamanio);
}
