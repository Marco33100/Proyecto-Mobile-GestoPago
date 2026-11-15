package com.proyecto.servicios.model.product;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductMessage {

    @JacksonXmlProperty(localName = "CODIGO")
    private String codigo;

    @JacksonXmlProperty(localName = "TEXTO")
    private String texto;
}
