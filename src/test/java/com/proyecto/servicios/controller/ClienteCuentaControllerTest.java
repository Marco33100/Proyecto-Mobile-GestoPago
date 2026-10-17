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
import java.util.Map;
import static com.proyecto.servicios.controller.ApiTestRequests.*;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClienteCuentaControllerTest {
    private final ClienteService clientes = mock(ClienteService.class);
    private final CuentaService cuentas = mock(CuentaService.class);
    private final ClienteRegistroService registro = mock(ClienteRegistroService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(clientes.consultar(any())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc = MockMvcBuilders.standaloneSetup(
                new ClienteController(clientes, registro),
                new CuentaController(cuentas))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
    }

    @Test
    void consultaCuentaYSaldoConPrefijoApi() throws Exception {
        String numero = "CTA-0123456789ABCDEF0123";
        var respuesta = new CuentaResponse(UUID.randomUUID(), numero, new BigDecimal("25.50"), true);
        when(cuentas.consultarPorNumeroCuenta(numero)).thenReturn(respuesta);
        when(cuentas.consultarSaldo(numero)).thenReturn(
                new com.proyecto.servicios.model.cliente.SaldoCuentaResponse(numero, new BigDecimal("25.50")));
        mvc.perform(cuentaConsulta(numero)).andExpect(status().isOk())
                .andExpect(jsonPath("datos.numeroCuenta").value(numero));
        mvc.perform(saldoConsulta(numero)).andExpect(status().isOk())
                .andExpect(jsonPath("datos.saldo").value(25.50));
        verify(cuentas).consultarPorNumeroCuenta(numero);
        verify(cuentas).consultarSaldo(numero);
    }

    @Test
    void enviaFiltrosAlServicio() throws Exception {
when(clientes.consultar(any())).thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0, true));
        mvc.perform(postJson("/api/clientes/consulta", Map.of("activo", true, "desde", "2026-10-01", "hasta", "2026-10-03")))
                .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(0))
                .andExpect(jsonPath("datos").doesNotExist());
        verify(clientes).consultar(argThat(request -> Boolean.TRUE.equals(request.getActivo())
                && LocalDate.of(2026, 10, 1).equals(request.getDesde())
                && LocalDate.of(2026, 10, 3).equals(request.getHasta())
                && request.getPagina() == 0 && request.getTamanio() == 20));
    }

    @Test
    void parametroInvalidoDevuelveJson400() throws Exception {
mvc.perform(postJson("/api/clientes/consulta", Map.of("desde", "no-es-fecha")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("no-es-fecha"))));
        verifyNoInteractions(clientes);
    }

    @Test
    void busquedaEntregaLosFiltrosAlServicioYValidaElDtoDeConsulta() throws Exception {
mvc.perform(postJson("/api/clientes/consulta", Map.of("nombre", "mAr", "rfc", "moma", "curp", "hdf",
                "pagina", 1, "tamanio", 5))).andExpect(status().isOk());
        verify(clientes).consultar(argThat(request -> request.getPagina() == 1 && request.getTamanio() == 5
                && "mAr".equals(request.getNombre()) && "moma".equals(request.getRfc()) && "hdf".equals(request.getCurp())));
        clearInvocations(clientes);
        for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("pagina", -1), Map.of("tamanio", 101),
                Map.of("nombre", "a".repeat(51)), Map.of("rfc", "a".repeat(14)), Map.of("curp", "a".repeat(19)),
                Map.of("pagina", 2147483648L))) {
            mvc.perform(postJson("/api/clientes/consulta", body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        }
        verifyNoInteractions(clientes);
    }

    @Test
    void fechaConHoraDevuelveJson400EnAltaYActualizacion() throws Exception {
String body = "{\"fechaNacimiento\":\"1990-01-01T12:30:00Z\"}";
        mvc.perform(post("/api/clientes").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        mvc.perform(put("/api/clientes").contentType("application/json").content("{\"id\":1,\"datos\":" + body + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(clientes);
    }

    @Test
    void cuentaInexistenteDevuelveJson404ConPath() throws Exception {
        String numero = "CTA-FFFFFFFFFFFFFFFFFFFF";
        when(cuentas.consultarPorNumeroCuenta(numero))
                .thenThrow(new CuentaNoEncontradaException("No existe la cuenta solicitada"));
        mvc.perform(cuentaConsulta(numero)).andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("path").doesNotExist());
    }

    @Test
    void conflictosConservanHttp409YMensajesSeguros() throws Exception {
when(clientes.consultar(any())).thenThrow(new CurpDuplicadaException(), new RfcDuplicadoException(),
                new ClienteYaRegistradoException());
        for (String mensaje : List.of("La CURP ya esta registrada", "El RFC ya esta registrado", "El cliente ya esta registrado")) {
            mvc.perform(postJson("/api/clientes/consulta", Map.of("correo", "uno@example.com")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").isString())
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
    }

    @Test
    void consultasExactasRecibenElIdentificadorDesdeElRequest() throws Exception {
for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("id", 7), Map.of("curp", "MALM900101HDFRPR01"),
                Map.of("rfc", "MALM900101AB1"), Map.of("numeroCuenta", "CTA-0123456789ABCDEF0123"),
                Map.of("correo", "cliente@example.com"))) {
            mvc.perform(postJson("/api/clientes/consulta", body)).andExpect(status().isOk());
        }
        verify(clientes).consultar(argThat(request -> Long.valueOf(7).equals(request.getId())));
        verify(clientes).consultar(argThat(request -> "MALM900101HDFRPR01".equals(request.getCurp())));
        verify(clientes).consultar(argThat(request -> "MALM900101AB1".equals(request.getRfc())));
        verify(clientes).consultar(argThat(request -> "CTA-0123456789ABCDEF0123".equals(request.getNumeroCuenta())));
        verify(clientes).consultar(argThat(request -> "cliente@example.com".equals(request.getCorreo())));
    }

    @Test
    void actualizacionYDesactivacionUsanUnSoloIdSinVariableDeRuta() throws Exception {
mvc.perform(put("/api/clientes").contentType("application/json")
                        .content("{\"id\":7,\"datos\":" + actualizacionValida() + "}"))
                .andExpect(status().isOk());
        verify(clientes).actualizar(eq(7L), argThat(request -> "Maria".equals(request.nombre())));
        mvc.perform(json(org.springframework.http.HttpMethod.DELETE, "/api/clientes", Map.of("id", 7)))
                .andExpect(status().isOk());
        verify(clientes).desactivar(7L);
    }

    @Test
    void idAusenteInvalidoODesbordadoDevuelveErroresDeCampoSinEjecutarElServicio() throws Exception {
for (String id : List.of("null", "0", "-1", "\"abc\"", "9223372036854775808")) {
            mvc.perform(put("/api/clientes").contentType("application/json")
                            .content("{\"id\":" + id + ",\"datos\":" + actualizacionValida() + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
            mvc.perform(delete("/api/clientes").contentType("application/json").content("{\"id\":" + id + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        }
        verifyNoInteractions(clientes);
    }

    @Test
    void rutasAntiguasNoEjecutanConsultasNiModificaciones() throws Exception {
        mvc.perform(get("/api/clientes/7")).andExpect(status().isNotFound());
        mvc.perform(put("/api/clientes/7").contentType("application/json").content(actualizacionValida()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/clientes/7")).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/curp/MALM900101HDFRPR01")).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/rfc/MALM900101AB1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes/cuenta/CTA-0123456789ABCDEF0123")).andExpect(status().isNotFound());
        verifyNoInteractions(clientes);
    }

    @Test
    void altaNoRecibeIdYRechazaPropiedadesControladasPorServidor() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = mapper.readTree(actualizacionValida());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("rfc", "MALM900101AB1")
                .put("curp", "MALM900101HDFRPR01").put("contrasena", "Registro123!");
        mvc.perform(post("/api/clientes").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated());
        verify(registro).registrar(any());
        clearInvocations(registro);
        for (String campo : List.of("id", "activo", "saldo", "version", "fechaCreacion", "propietario")) {
            var invalido = body.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) invalido).put(campo, "valor-no-permitido");
            mvc.perform(post("/api/clientes").contentType("application/json").content(invalido.toString()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)));
        }
        verifyNoInteractions(registro, clientes);
    }

    @Test
    void filtrosSoloSeLeenDelCuerpoYNuncaDeLaUrl() throws Exception {
        mvc.perform(postJson("/api/clientes/consulta", Map.of("nombre", "Maria"))
                        .param("nombre", "Otro").param("id", "999"))
                .andExpect(status().isOk());
        verify(clientes).consultar(argThat(request -> "Maria".equals(request.getNombre()) && request.getId() == null));
        clearInvocations(clientes);
        mvc.perform(post("/api/clientes/consulta").param("nombre", "Maria").contentType("application/json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value(2));
        verifyNoInteractions(clientes);
    }

    private String actualizacionValida() {
        return """
                {"nombre":"Maria","apellidoPaterno":"Lopez","apellidoMaterno":"Perez",
                 "fechaNacimiento":"1990-01-01","sexo":"FEMENINO","nacionalidad":"Mexicana",
                 "estadoCivil":"SOLTERO","correo":"cliente@example.com","telefonoMovil":"6131978871",
                 "ocupacion":"Docente","empresa":"Escuela","ingresoMensual":1000.00,
                 "domicilio":{"calle":"Reforma","numeroExterior":"1","colonia":"Centro",
                 "municipio":"La Paz","estado":"Baja California Sur","codigoPostal":"23000","pais":"Mexico"}}
                """;
    }
}
