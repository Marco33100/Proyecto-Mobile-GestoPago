package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface GestoPagoTokenMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idDistribuidor", ignore = true)
    @Mapping(target = "codigoDispositivo", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    GestoPagoToken toEntity(GestoPagoAuthResponse response);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "idDistribuidor", ignore = true)
    @Mapping(target = "codigoDispositivo", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    void updateEntity(GestoPagoAuthResponse response, @MappingTarget GestoPagoToken entity);
}
