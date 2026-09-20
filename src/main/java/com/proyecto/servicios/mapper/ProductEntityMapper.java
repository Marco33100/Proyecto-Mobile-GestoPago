package com.proyecto.servicios.mapper;

import com.proyecto.servicios.entity.gestopago.ProductEntity;
import com.proyecto.servicios.model.product.ProductDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProductEntityMapper {

    public ProductEntity toEntity(ProductDto source) {
        ProductEntity target = new ProductEntity();
        target.setProductId(source.getIdProducto());
        target.setServiceId(source.getIdServicio());
        target.setCategoryServiceTypeId(source.getIdCatTipoServicio());
        target.setServiceName(source.getServicio());
        target.setProductName(source.getProducto());
        target.setFrontType(source.getTipoFront());
        target.setHasCheckDigit(source.getHasDigitoVerificador());
        target.setPrice(source.getPrecio());
        target.setShowHelp(source.getShowAyuda());
        target.setReferenceType(source.getTipoReferencia());
        target.setLegend(source.getLegend());
        target.setUpdatedAt(LocalDateTime.now());
        return target;
    }

    public ProductDto toDto(ProductEntity source) {
        ProductDto target = new ProductDto();
        target.setIdProducto(source.getProductId());
        target.setIdServicio(source.getServiceId());
        target.setIdCatTipoServicio(source.getCategoryServiceTypeId());
        target.setServicio(source.getServiceName());
        target.setProducto(source.getProductName());
        target.setTipoFront(source.getFrontType());
        target.setHasDigitoVerificador(source.getHasCheckDigit());
        target.setPrecio(source.getPrice());
        target.setShowAyuda(source.getShowHelp());
        target.setTipoReferencia(source.getReferenceType());
        target.setLegend(source.getLegend());
        return target;
    }
}
