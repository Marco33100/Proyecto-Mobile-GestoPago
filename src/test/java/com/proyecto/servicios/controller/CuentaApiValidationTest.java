package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.cliente.CuentaConflictException;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CuentaApiValidationTest {
    private static final String NUMERO = "CTA-0123456789ABCDEF0123";
    private static final String RUTA = "/api/cuentas/" + NUMERO;
    private final CuentaService cuentas = mock(CuentaService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new CuentaController(cuentas),
                        new ClienteController(clientes, mock(ClienteRegistroService.class)))
                .setControllerAdvice(new GlobalApiExceptionHandler()).build();
    }

    @Test
    void patchRecibeSoloElEstadoYUnIdentificadorEnLaRuta() throws Exception {
        var payload = new ActualizarEstadoCuentaRequest(false);
        when(cuentas.actualizarEstado(NUMERO, payload))
                .thenReturn(new CuentaResponse(UUID.randomUUID(), NUMERO, new BigDecimal("10.50"), false));
        mvc.perform(patch(RUTA).contentType("application/json").content("{\"activa\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("activa").value(false))
                .andExpect(jsonPath("saldo").value(10.50));
        verify(cuentas).actualizarEstado(NUMERO, payload);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void consultaDistingueElIdInternoDelNumeroPublicoSinRecibirIdentificadoresAdicionales() throws Exception {
        UUID idInterno = UUID.randomUUID();
        when(cuentas.consultarPorNumeroCuenta(NUMERO))
                .thenReturn(new CuentaResponse(idInterno, NUMERO, BigDecimal.TEN, true));

        mvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(idInterno.toString()))
                .andExpect(jsonPath("numeroCuenta").value(NUMERO));

        verify(cuentas).consultarPorNumeroCuenta(NUMERO);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void cuentaInexistenteDevuelve404EnConsultaSaldoPatchYBajaSinFiltrarDetallesInternos() throws Exception {
        var error = new CuentaNoEncontradaException("jdbc:postgresql://interno?password=fixture-secret");
        var payload = new ActualizarEstadoCuentaRequest(false);
        when(cuentas.consultarPorNumeroCuenta(NUMERO)).thenThrow(error);
        when(cuentas.consultarSaldo(NUMERO)).thenThrow(error);
        when(cuentas.actualizarEstado(NUMERO, payload)).thenThrow(error);
        when(cuentas.desactivar(NUMERO)).thenThrow(error);

        for (var request : List.of(get(RUTA), get(RUTA + "/saldo"),
                patch(RUTA).contentType("application/json").content("{\"activa\":false}"), delete(RUTA))) {
            mvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("status").value(404))
                    .andExpect(jsonPath("code").value("ACCOUNT_NOT_FOUND"))
                    .andExpect(jsonPath("message").value("No existe la cuenta solicitada"))
                    .andExpect(jsonPath("fieldErrors").isMap())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("fixture-secret"))));
        }

        verify(cuentas).consultarPorNumeroCuenta(NUMERO);
        verify(cuentas).consultarSaldo(NUMERO);
        verify(cuentas).actualizarEstado(NUMERO, payload);
        verify(cuentas).desactivar(NUMERO);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void patchNoAceptaIdentificadoresSaldoTitularNiCamposDesconocidos() throws Exception {
        for (String field : List.of("numeroCuenta", "id", "saldo", "clienteId", "titular", "otro")) {
            mvc.perform(patch(RUTA).contentType("application/json")
                            .content("{\"activa\":false,\"" + field + "\":\"dato-secreto\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST_BODY"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dato-secreto"))));
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void patchRechazaCuerpoVacioNuloOTipoIncorrecto() throws Exception {
        for (String body : List.of("", "null", "{}", "{\"activa\":null}", "{\"activa\":\"false\"}",
                "{\"activa\":0}", "{\"activa\":[]}", "{", "[]")) {
            mvc.perform(patch(RUTA).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400))
                    .andExpect(jsonPath("path").value(RUTA)).andExpect(jsonPath("fieldErrors").isMap());
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void reactivarDevuelveConflictoDeNegocioNoErrorInterno() throws Exception {
        when(cuentas.actualizarEstado(NUMERO, new ActualizarEstadoCuentaRequest(true)))
                .thenThrow(new CuentaConflictException("La reactivacion de cuentas no esta permitida"));
        mvc.perform(patch(RUTA).contentType("application/json").content("{\"activa\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("ACCOUNT_STATE_CONFLICT"));
    }

    @Test
    void identificadoresInvalidosNoLleganALosServicios() throws Exception {
        for (String number : List.of("CTA-123", "CTA-0123456789ABCDEG0123", "1234567890123456789012345", "NOEXISTE",
                UUID.randomUUID().toString())) {
            mvc.perform(get("/api/cuentas/" + number)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/cuentas/" + number + "/saldo")).andExpect(status().isBadRequest());
            mvc.perform(delete("/api/cuentas/" + number)).andExpect(status().isBadRequest());
            mvc.perform(patch("/api/cuentas/" + number).contentType("application/json").content("{\"activa\":false}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/clientes/cuenta/" + number)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cuentas, clientes);
    }

    @Test
    void paginacionInvalidaSeRechazaAntesDelServicio() throws Exception {
        for (String[] args : List.of(new String[]{"-1", "10"}, new String[]{"0", "0"},
                new String[]{"0", "101"}, new String[]{"texto", "10"}, new String[]{"2147483648", "10"})) {
            mvc.perform(get("/api/cuentas/activas").param("pagina", args[0]).param("tamanio", args[1]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void idClienteNoPositivoOMalFormadoNoConsultaLaBase() throws Exception {
        for (String id : List.of("0", "-1", "no-numero", "9223372036854775808")) {
            mvc.perform(get("/api/clientes/" + id)).andExpect(status().isBadRequest());
            mvc.perform(delete("/api/clientes/" + id)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(clientes);
    }

    @Test
    void erroresDeProtocoloMantienenStatusCabecerasYFormatoUniforme() throws Exception {
        mvc.perform(put(RUTA).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(patch(RUTA).contentType("text/plain").content("activa=false"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get(RUTA).accept("application/xml"))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("code").value("NOT_ACCEPTABLE"));
        mvc.perform(get("/api/clientes/correo"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(cuentas, clientes);
    }

    @Test
    void curpRfcYCorreoInvalidosNoLleganAlServicio() throws Exception {
        for (String curp : List.of("X", "MOMA901301HDFRRR01", "MOMA900101HDFRRR011")) {
            mvc.perform(get("/api/clientes/curp/" + curp)).andExpect(status().isBadRequest());
        }
        for (String rfc : List.of("X", "MOMA901301AB1", "MOMA900101AB12")) {
            mvc.perform(get("/api/clientes/rfc/" + rfc)).andExpect(status().isBadRequest());
        }
        for (String correo : List.of("", " ", "sin-arroba", "a".repeat(101) + "@example.com")) {
            mvc.perform(get("/api/clientes/correo").param("correo", correo)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(clientes);
    }
}
