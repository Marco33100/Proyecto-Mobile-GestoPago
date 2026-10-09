package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;

public interface CuentaService {

    CuentaEntity crearPara(ClienteEntity cliente);

    CuentaResponse consultarPorNumeroCuenta(String numeroCuenta);

    CuentaResponse desactivar(String numeroCuenta);

    PaginaResponse<CuentaResponse> consultarActivas(int pagina, int tamanio);
}
