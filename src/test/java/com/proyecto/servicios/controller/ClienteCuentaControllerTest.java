package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.cliente.CurpDuplicadaException;
import com.proyecto.servicios.exception.cliente.RfcDuplicadoException;
import com.proyecto.servicios.exception.cliente.ClienteYaRegistradoException;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClienteCuentaControllerTest {
    private final ClienteService clientes = mock(ClienteService.class);
    private final CuentaService cuentas = mock(CuentaService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new ClienteController(clientes, mock(ClienteRegistroService.class)),
                new CuentaController(cuentas))
                .setControllerAdvice(new ClienteExceptionHandler()).build();
    }

    @Test
    void consultaCuentaYSaldoConPrefijoApi() throws Exception {
        var respuesta = new CuentaResponse(UUID.randomUUID(), "CTA-123", new BigDecimal("25.50"), true);
        when(cuentas.consultarPorNumeroCuenta("CTA-123")).thenReturn(respuesta);
        mvc.perform(get("/api/cuentas/CTA-123")).andExpect(status().isOk())
                .andExpect(jsonPath("numeroCuenta").value("CTA-123"));
        mvc.perform(get("/api/cuentas/CTA-123/saldo")).andExpect(status().isOk())
                .andExpect(jsonPath("saldo").value(25.50));
    }

    @Test
    void enviaFiltrosAlServicio() throws Exception {
        when(clientes.consultarTodos(0, 20, true, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc.perform(get("/api/clientes").param("activo", "true").param("desde", "2026-10-01")
                .param("hasta", "2026-10-03")).andExpect(status().isOk());
        verify(clientes).consultarTodos(0, 20, true,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
    }

    @Test
    void parametroInvalidoDevuelveJson400() throws Exception {
        mvc.perform(get("/api/clientes").param("desde", "no-es-fecha"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_QUERY_PARAMETER"));
        verifyNoInteractions(clientes);
    }

    @Test
    void fechaConHoraDevuelveJson400EnAltaYActualizacion() throws Exception {
        String body = "{\"fechaNacimiento\":\"1990-01-01T12:30:00Z\"}";
        mvc.perform(post("/api/clientes").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("path").value("/api/clientes"));
        mvc.perform(put("/api/clientes/1").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_REQUEST_BODY"));
        verifyNoInteractions(clientes);
    }

    @Test
    void cuentaInexistenteDevuelveJson404ConPath() throws Exception {
        when(cuentas.consultarPorNumeroCuenta("NOEXISTE"))
                .thenThrow(new CuentaNoEncontradaException("No existe la cuenta solicitada"));
        mvc.perform(get("/api/cuentas/NOEXISTE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("path").value("/api/cuentas/NOEXISTE"));
    }

    @Test
    void conflictosTienenCodigosEspecificos() throws Exception {
        when(clientes.consultarPorCorreo("uno@example.com"))
                .thenThrow(new CurpDuplicadaException(), new RfcDuplicadoException(),
                        new ClienteYaRegistradoException());
        for (String code : List.of("DUPLICATE_CURP", "DUPLICATE_RFC", "CLIENT_ALREADY_REGISTERED")) {
            mvc.perform(get("/api/clientes/correo").param("correo", "uno@example.com"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("code").value(code));
        }
    }
}
