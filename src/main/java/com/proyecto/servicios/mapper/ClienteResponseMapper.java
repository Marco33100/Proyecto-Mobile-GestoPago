package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.DomicilioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClienteResponseMapper {

    @Mapping(target = ".", source = "cliente")
    @Mapping(target = "id", source = "cliente.id")
    @Mapping(target = "fechaCreacion", source = "cliente.fechaCreacion")
    @Mapping(target = "fechaActualizacion", source = "cliente.fechaActualizacion")
    @Mapping(target = "domicilio", source = "domicilio")
    @Mapping(target = "cuentas", source = "cuentas")
    ClienteResponse toResponse(
            ClienteEntity cliente,
            DomicilioEntity domicilio,
            List<CuentaEntity> cuentas
    );

    DomicilioResponse toDomicilioResponse(DomicilioEntity domicilio);

    CuentaResponse toCuentaResponse(CuentaEntity cuenta);
}
