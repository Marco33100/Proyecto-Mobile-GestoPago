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
import java.util.Map;
import static com.proyecto.servicios.controller.ApiTestRequests.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CuentaApiValidationTest {
    private static final String NUMERO = "CTA-0123456789ABCDEF0123";
    private static final String RUTA = "/api/cuentas";
    private final CuentaService cuentas = mock(CuentaService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        when(clientes.consultar(any())).thenReturn(new com.proyecto.servicios.model.cliente.PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc = MockMvcBuilders.standaloneSetup(new CuentaController(cuentas),
                        new ClienteController(clientes, mock(ClienteRegistroService.class)))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
    }

    @Test
    void patchRecibeSoloElEstadoYUnIdentificadorEnElRequest() throws Exception {
        var payload = new ActualizarEstadoCuentaRequest(false);
        when(cuentas.actualizarEstado(NUMERO, payload))
                .thenReturn(new CuentaResponse(UUID.randomUUID(), NUMERO, new BigDecimal("10.50"), false));
        mvc.perform(parcheCuenta(NUMERO, "{\"activa\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.activa").value(false))
                .andExpect(jsonPath("datos.saldo").value(10.50));
        verify(cuentas).actualizarEstado(NUMERO, payload);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void consultaDistingueElIdInternoDelNumeroPublicoSinRecibirIdentificadoresAdicionales() throws Exception {
        UUID idInterno = UUID.randomUUID();
        when(cuentas.consultarPorNumeroCuenta(NUMERO))
                .thenReturn(new CuentaResponse(idInterno, NUMERO, BigDecimal.TEN, true));

        mvc.perform(cuentaConsulta(NUMERO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.id").value(idInterno.toString()))
                .andExpect(jsonPath("datos.numeroCuenta").value(NUMERO));

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

        for (var request : List.of(cuentaConsulta(NUMERO), saldoConsulta(NUMERO),
                parcheCuenta(NUMERO, "{\"activa\":false}"), bajaCuenta(NUMERO))) {
            mvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").value("No existe la cuenta solicitada"))
                    .andExpect(jsonPath("fieldErrors").doesNotExist())
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
            mvc.perform(parcheCuenta(NUMERO, "{\"activa\":false,\"" + field + "\":\"dato-secreto\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dato-secreto"))));
        }
        mvc.perform(patch(RUTA).contentType("application/json")
                        .content("{\"numeroCuenta\":\"" + NUMERO + "\",\"cambios\":{\"activa\":false},\"saldo\":500}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void patchRechazaCuerpoVacioNuloOTipoIncorrecto() throws Exception {
for (String body : List.of("", "null", "{}", "{\"cambios\":null}", "{\"numeroCuenta\":\"" + NUMERO + "\"}",
                "{\"numeroCuenta\":\"" + NUMERO + "\",\"cambios\":{}}",
                "{\"numeroCuenta\":\"" + NUMERO + "\",\"cambios\":{\"activa\":null}}", "{", "[]")) {
            mvc.perform(patch(RUTA).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
        for (String activa : List.of("\"false\"", "0", "[]")) {
            mvc.perform(parcheCuenta(NUMERO, "{\"activa\":" + activa + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void reactivarDevuelveConflictoDeNegocioNoErrorInterno() throws Exception {
        when(cuentas.actualizarEstado(NUMERO, new ActualizarEstadoCuentaRequest(true)))
                .thenThrow(new CuentaConflictException("La reactivacion de cuentas no esta permitida"));
        mvc.perform(parcheCuenta(NUMERO, "{\"activa\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("codigo").value(2));
    }

    @Test
    void identificadoresInvalidosNoLleganALosServicios() throws Exception {
for (String number : List.of("", "CTA-123", "CTA-0123456789ABCDEG0123", "1234567890123456789012345",
                "NOEXISTE", UUID.randomUUID().toString())) {
            for (var request : List.of(cuentaConsulta(number), saldoConsulta(number), bajaCuenta(number),
                    parcheCuenta(number, "{\"activa\":false}"),
                    postJson("/api/clientes/consulta", Map.of("numeroCuenta", number)))) {
                mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
            }
        }
        verifyNoInteractions(cuentas, clientes);
    }

    @Test
    void numeroCuentaAusenteDevuelveMensajeDeCampoEnCadaOperacion() throws Exception {
for (var request : List.of(postJson(RUTA + "/consulta", Map.of()), postJson(RUTA + "/saldo/consulta", Map.of()),
                json(org.springframework.http.HttpMethod.DELETE, RUTA, Map.of()),
                json(org.springframework.http.HttpMethod.PATCH, RUTA, Map.of("cambios", Map.of("activa", false))))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").value(org.hamcrest.Matchers.containsString(
                            "numeroCuenta=El numero de cuenta es obligatorio")));
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void consultaUnificadaPermiteOmitirFiltrosYRetiraLasRutasPorCampo() throws Exception {
// Los filtros del endpoint unificado son opcionales, no identificadores obligatorios separados.
        mvc.perform(postJson("/api/clientes/consulta", Map.of())).andExpect(status().isOk());
        verify(clientes).consultar(argThat(request -> request.getId() == null && request.getRfc() == null
                && request.getCurp() == null && request.getPagina() == 0 && request.getTamanio() == 20));
        clearInvocations(clientes);
        for (String ruta : List.of("/api/clientes/rfc", "/api/clientes/curp", "/api/clientes/correo", "/api/clientes/detalle")) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound());
        }
        verifyNoInteractions(clientes);
    }

    @Test
    void rutasAntiguasDeCuentaYaNoPermitenEjecutarOperaciones() throws Exception {
        for (var request : List.of(get(RUTA + "/" + NUMERO), get(RUTA + "/" + NUMERO + "/saldo"),
                delete(RUTA + "/" + NUMERO), patch(RUTA + "/" + NUMERO)
                        .contentType("application/json").content("{\"activa\":false}"))) {
            mvc.perform(request).andExpect(status().isNotFound());
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void paginacionInvalidaSeRechazaAntesDelServicio() throws Exception {
for (Object[] args : List.of(new Object[]{-1, 10}, new Object[]{0, 0}, new Object[]{0, 101},
                new Object[]{"texto", 10}, new Object[]{2147483648L, 10})) {
            for (String ruta : List.of("/api/cuentas/activas/consulta", "/api/cuentas/mias/consulta")) {
                mvc.perform(postJson(ruta, Map.of("pagina", args[0], "tamanio", args[1])))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
            }
        }
        verifyNoInteractions(cuentas);
    }

    @Test
    void idClienteNoPositivoOMalFormadoNoConsultaLaBase() throws Exception {
for (Object id : List.of(0, -1, "no-numero", "9223372036854775808")) {
            mvc.perform(postJson("/api/clientes/consulta", Map.of("id", id))).andExpect(status().isBadRequest());
            mvc.perform(json(org.springframework.http.HttpMethod.DELETE, "/api/clientes", Map.of("id", id)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(clientes);
    }

    @Test
    void erroresDeProtocoloMantienenStatusCabecerasYFormatoUniforme() throws Exception {
mvc.perform(put(RUTA).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("codigo").value(2));
        mvc.perform(patch(RUTA).contentType("text/plain").content("activa=false"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("codigo").value(2));
        mvc.perform(cuentaConsulta(NUMERO).accept("application/xml"))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("codigo").value(2));
        mvc.perform(post("/api/clientes/consulta").param("rfc", "MOMA900101AB1").contentType("application/json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(cuentas, clientes);
    }

    @Test
    void curpRfcYCorreoInvalidosNoLleganAlServicio() throws Exception {
// RFC/CURP son filtros parciales: se limitan por longitud, no por el patron del identificador completo.
        for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("curp", "a".repeat(19)),
                Map.of("rfc", "a".repeat(14)), Map.of("nombre", "a".repeat(51)),
                Map.of("correo", ""), Map.of("correo", " "), Map.of("correo", "sin-arroba"),
                Map.of("correo", "a".repeat(101) + "@example.com"))) {
            mvc.perform(postJson("/api/clientes/consulta", body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        }
        verifyNoInteractions(clientes);
    }
}
