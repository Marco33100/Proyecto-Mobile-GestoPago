package com.proyecto.servicios.model.product;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductListXmlMappingTest {

    private final XmlMapper xmlMapper = new XmlMapper();

    @Test
    void mapsPuntoRedXmlResponse() throws Exception {
        String xml = """
                <RESPONSE>
                    <MENSAJE>
                        <CODIGO>01</CODIGO>
                        <TEXTO>Operacion realizada con exito</TEXTO>
                    </MENSAJE>
                    <PRODUCTOS>
                        <producto servicio="Amazon" producto="Amazon $100"
                            idServicio="71" idProducto="200" idCatTipoServicio="10"
                            tipoFront="1" hasDigitoVerificador="false" precio="100.0"
                            showAyuda="false" tipoReferencia="a">
                            <legend>Instrucciones del producto</legend>
                        </producto>
                    </PRODUCTOS>
                </RESPONSE>
                """;

        ProductListResponse response = xmlMapper.readValue(xml, ProductListResponse.class);

        assertEquals("01", response.getMensaje().getCodigo());
        assertNotNull(response.getProductos());
        assertEquals(1, response.getProductos().getProductos().size());

        ProductDto product = response.getProductos().getProductos().get(0);
        assertEquals(200, product.getIdProducto());
        assertEquals(new BigDecimal("100.0"), product.getPrecio());
        assertFalse(product.getHasDigitoVerificador());
    }
}
