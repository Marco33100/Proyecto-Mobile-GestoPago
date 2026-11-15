package com.proyecto.servicios.model.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "RESPONSE")
public class ProductListResponse {

    @JacksonXmlProperty(localName = "MENSAJE")
    private ProductMessage mensaje;

    @JacksonXmlProperty(localName = "PRODUCTOS")
    private ProductContainer productos;
}
