package com.proyecto.servicios.service;
import com.proyecto.servicios.model.pago.PagoDtos.*;

public interface SegundoFactorService {
    EstadoFactorResponse estado();
    PrepararFactorResponse preparar(PrepararFactorRequest request);
    ActivarFactorResponse activar(ActivarFactorRequest request);
}
