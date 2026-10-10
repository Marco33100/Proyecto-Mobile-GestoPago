package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.ProductCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalApiExceptionHandlerTest {
    @Test void fallaAlAbrirOConfirmarTransaccionDevuelveJson503Seguro() throws Exception {
        for (RuntimeException error : List.of(new CannotCreateTransactionException("password=secreto"),
                new TransactionSystemException("jdbc://servidor-interno"),
                new DataAccessResourceFailureException("detalle sql"))) {
            verificar(error, 503, "CLIENT_DATABASE_ERROR", "No fue posible completar la operacion de clientes o cuentas");
        }
    }

    @Test void conflictoOptimistaDevuelve409ParaPoderReintentar() throws Exception {
        verificar(new OptimisticLockingFailureException("sql interno"), 409, "CLIENT_CONCURRENT_UPDATE",
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez");
    }

    @Test void excepcionInesperadaNoExponeMensajesInternos() throws Exception {
        verificar(new IllegalStateException("token=secreto"), 500, "CLIENT_INTERNAL_ERROR", "Ocurrio un error interno");
    }

    @Test void recursoInexistenteNoRevelaLosDatosDeLaConsulta() throws Exception {
        verificar(new ClienteNoEncontradoException("CURP=secreto"), 404, "CLIENT_NOT_FOUND",
                "No existe el cliente solicitado");
    }

    @Test void denegacionEnServicioNoSeConvierteEnErrorInterno() throws Exception {
        verificar(new AccessDeniedException("rol interno secreto"), 403, "AUTH_FORBIDDEN",
                "No tienes permisos para realizar esta operacion");
    }

    @Test void integracionUsaElMismoFormatoSinRevelarCredencialesDelProveedor() throws Exception {
        for (ProductIntegrationErrorType type : ProductIntegrationErrorType.values()) {
            var catalogo = mock(ProductCatalogService.class);
            when(catalogo.obtenerProductos()).thenThrow(new ProductIntegrationException(type,
                    "https://interno/auth?password=secreto", new IllegalStateException("token=secreto")));
            var mvc = MockMvcBuilders.standaloneSetup(new ProductCatalogController(catalogo))
                    .setControllerAdvice(new GlobalApiExceptionHandler()).build();
            boolean proveedor = type == ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE;
            mvc.perform(get("/api/products")).andExpect(status().is(proveedor ? 502 : 500))
                    .andExpect(jsonPath("code").value(proveedor ? "GESTOPAGO_UNAVAILABLE" : "PRODUCT_DATABASE_ERROR"))
                    .andExpect(jsonPath("status").value(proveedor ? 502 : 500))
                    .andExpect(jsonPath("timestamp").exists())
                    .andExpect(jsonPath("path").value("/api/products"))
                    .andExpect(jsonPath("fieldErrors").isMap())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(not(containsString("secreto"))))
                    .andExpect(content().string(not(containsString("https://interno"))));
        }
    }

    private void verificar(RuntimeException error, int status, String code, String mensaje) throws Exception {
        var clientes = mock(ClienteService.class);
        when(clientes.consultarPorId(1L)).thenThrow(error);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, mock(ClienteRegistroService.class)))
                .setControllerAdvice(new GlobalApiExceptionHandler()).build();
        mvc.perform(get("/api/clientes/1")).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("code").value(code)).andExpect(jsonPath("message").value(mensaje))
                .andExpect(jsonPath("path").value("/api/clientes/1"))
                .andExpect(jsonPath("timestamp").exists())
                .andExpect(jsonPath("fieldErrors").isMap())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("jdbc://"))))
                .andExpect(content().string(not(containsString("sql interno"))));
    }
}
