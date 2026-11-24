package com.proyecto.servicios.service;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.pago.PagoDtos.*;
public interface PagoService {
    PagoResponse preparar(PrepararRequest request);
    PagoResponse autorizar(AutorizarRequest request);
    PagoResponse consultar(ConsultaRequest request);
    PagoResponse cancelar(ConsultaRequest request);
    /** Compras exitosas del cliente autenticado, paginadas. */
    PaginaResponse<CompraResponse> historial(com.proyecto.servicios.model.pago.HistorialRequest request);
}
