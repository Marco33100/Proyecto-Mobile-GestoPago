package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.proyecto.servicios.controller.ApiTestRequests.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResultadoApiContractTest {
    private static final String NUMERO = "CTA-0123456789ABCDEF0123";
    private final ClienteService clientes = mock(ClienteService.class);
    private final CuentaService cuentas = mock(CuentaService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, mock(ClienteRegistroService.class)),
                        new CuentaController(cuentas))
                .setControllerAdvice(new ValidadorCampos(), new GlobalApiExceptionHandler()).build();
    }

    @Test
    void consultaSinResultadosEsCodigo0ConHttp200YSinDatos() throws Exception {
        when(clientes.consultar(any())).thenReturn(new PaginaResponse<>(List.of(), 0, 10, 0, 0, true));
        mvc.perform(postJson("/api/clientes/consulta", Map.of("tamanio", 10)))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("mensaje").value("No se encontraron datos"))
                .andExpect(jsonPath("datos").doesNotExist())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
    }

    @Test
    void paginaFueraDeRangoEsCodigo0SinDatosYNoSeConvierteEnError() throws Exception {
        when(clientes.consultar(any())).thenReturn(new PaginaResponse<>(List.of(), 2, 10, 12, 2, true));
        mvc.perform(postJson("/api/clientes/consulta", Map.of("pagina", 2, "tamanio", 10)))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("mensaje").value("No se encontraron datos"))
                .andExpect(jsonPath("datos").doesNotExist())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
    }

    @Test
    void listadosDeCuentasSinResultadosTambienOmitenDatos() throws Exception {
        PaginaResponse<CuentaResponse> vacia = new PaginaResponse<>(List.of(), 0, 20, 0, 0, true);
        when(cuentas.consultarActivas(0, 20)).thenReturn(vacia);
        when(cuentas.consultarPropias(0, 20)).thenReturn(vacia);
        for (String ruta : List.of("/api/cuentas/activas/consulta", "/api/cuentas/mias/consulta")) {
            mvc.perform(postJson(ruta, Map.of())).andExpect(status().isOk())
                    .andExpect(jsonPath("codigo").value(0))
                    .andExpect(jsonPath("mensaje").value("No se encontraron datos"))
                    .andExpect(jsonPath("datos").doesNotExist())
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
    }

    @Test
    void catalogoSinProductosTambienDevuelveSoloCodigoYMensaje() throws Exception {
        var catalogo = mock(com.proyecto.servicios.service.ProductCatalogService.class);
        var respuesta = new com.proyecto.servicios.model.product.ProductListResponse();
        respuesta.setProductos(new com.proyecto.servicios.model.product.ProductContainer());
        when(catalogo.obtenerProductos()).thenReturn(respuesta);
        var mvcCatalogo = MockMvcBuilders.standaloneSetup(new ProductCatalogController(catalogo))
                .setControllerAdvice(new ValidadorCampos(), new GlobalApiExceptionHandler()).build();
        mvcCatalogo.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("mensaje").value("No se encontraron datos"))
                .andExpect(jsonPath("datos").doesNotExist())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
    }

    @Test
    void cuentaConSaldoCeroSiFueEncontradaEsCodigo1() throws Exception {
        when(cuentas.consultarSaldo(NUMERO)).thenReturn(new SaldoCuentaResponse(NUMERO, BigDecimal.ZERO));
        mvc.perform(saldoConsulta(NUMERO)).andExpect(status().isOk())
                .andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos.numeroCuenta").value(NUMERO))
                .andExpect(jsonPath("datos.saldo").value(0))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(3)));
        verify(cuentas).consultarSaldo(NUMERO);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void listadoConDatosEsCodigo1YSigueDevolviendoLaPaginaCompleta() throws Exception {
        var cuenta = new CuentaResponse(UUID.randomUUID(), NUMERO, BigDecimal.ZERO, true);
        when(cuentas.consultarPropias(0, 20)).thenReturn(new PaginaResponse<>(List.of(cuenta), 0, 20, 1, 1, true));
        mvc.perform(postJson("/api/cuentas/mias/consulta", Map.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(1))
                .andExpect(jsonPath("datos.contenido[0].numeroCuenta").value(NUMERO))
                .andExpect(jsonPath("datos.totalElementos").value(1));
    }

    @Test
    void recursoInexistenteEsCodigo2SinDatosYConHttp404() throws Exception {
        when(cuentas.consultarSaldo(NUMERO)).thenThrow(
                new com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException("detalle interno"));
        mvc.perform(saldoConsulta(NUMERO)).andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").value("No existe la cuenta solicitada"))
                .andExpect(jsonPath("datos").doesNotExist())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
    }
}
