package com.proyecto.servicios.service;

import com.proyecto.servicios.model.pago.ConciliacionDtos.*;
import com.proyecto.servicios.model.cliente.PaginacionRequest;
import com.proyecto.servicios.model.cliente.PaginaResponse;

public interface ConciliacionPagoService {
    PaginaResponse<Caso> pendientes(PaginacionRequest request);
    Propuesta proponer(ProponerRequest request);
    Propuesta consultar(PropuestaRequest request);
    Propuesta aprobar(DecisionRequest request);
    Propuesta rechazar(DecisionRequest request);
}
