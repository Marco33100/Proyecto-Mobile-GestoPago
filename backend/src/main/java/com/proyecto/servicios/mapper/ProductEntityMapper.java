package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.ProductEntity;
import com.proyecto.servicios.model.product.ProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Collection;
import java.util.List;

/** Convierte el modelo deserializado desde XML en la entidad persistida. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductEntityMapper {

    @Mapping(target = "productId", source = "idProducto")
    @Mapping(target = "serviceId", source = "idServicio")
    @Mapping(target = "categoryServiceTypeId", source = "idCatTipoServicio")
    @Mapping(target = "serviceName", source = "servicio")
    @Mapping(target = "productName", source = "producto")
    @Mapping(target = "frontType", source = "tipoFront")
    @Mapping(target = "hasCheckDigit", source = "hasDigitoVerificador")
    @Mapping(target = "price", source = "precio")
    @Mapping(target = "showHelp", source = "showAyuda")
    @Mapping(target = "referenceType", source = "tipoReferencia")
    @Mapping(target = "legend", source = "legend")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
    ProductEntity toEntity(ProductDto source);

    @Mapping(target = "idProducto", source = "productId")
    @Mapping(target = "idServicio", source = "serviceId")
    @Mapping(target = "idCatTipoServicio", source = "categoryServiceTypeId")
    @Mapping(target = "servicio", source = "serviceName")
    @Mapping(target = "producto", source = "productName")
    @Mapping(target = "tipoFront", source = "frontType")
    @Mapping(target = "hasDigitoVerificador", source = "hasCheckDigit")
    @Mapping(target = "precio", source = "price")
    @Mapping(target = "showAyuda", source = "showHelp")
    @Mapping(target = "tipoReferencia", source = "referenceType")
    @Mapping(target = "legend", source = "legend")
    ProductDto toDto(ProductEntity source);

    List<ProductEntity> toEntities(Collection<ProductDto> source);

    List<ProductDto> toDtos(Collection<ProductEntity> source);
}
