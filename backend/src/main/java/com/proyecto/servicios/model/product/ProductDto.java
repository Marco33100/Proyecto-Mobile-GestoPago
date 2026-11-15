package com.proyecto.servicios.model.product;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ProductDto {

    @JacksonXmlProperty(isAttribute = true, localName = "servicio")
    private String servicio;

    @JacksonXmlProperty(isAttribute = true, localName = "producto")
    private String producto;

    @JacksonXmlProperty(isAttribute = true, localName = "idServicio")
    private Integer idServicio;

    @JacksonXmlProperty(isAttribute = true, localName = "idProducto")
    private Integer idProducto;

    @JacksonXmlProperty(isAttribute = true, localName = "idCatTipoServicio")
    private Integer idCatTipoServicio;

    @JacksonXmlProperty(isAttribute = true, localName = "tipoFront")
    private Integer tipoFront;

    @JacksonXmlProperty(isAttribute = true, localName = "hasDigitoVerificador")
    private Boolean hasDigitoVerificador;

    @JacksonXmlProperty(isAttribute = true, localName = "precio")
    private BigDecimal precio;

    @JacksonXmlProperty(isAttribute = true, localName = "showAyuda")
    private Boolean showAyuda;

    @JacksonXmlProperty(isAttribute = true, localName = "tipoReferencia")
    private String tipoReferencia;

    @JacksonXmlProperty(localName = "legend")
    private String legend;
}
