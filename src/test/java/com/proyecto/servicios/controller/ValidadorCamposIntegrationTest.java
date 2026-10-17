package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.ProductCatalogService;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.Impl.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.proyecto.servicios.controller.ApiTestRequests.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ValidadorCamposIntegrationTest {
    @Test
    void validacionDelLoginUsaSoloCodigoYMensajeConHttp400() throws Exception {
        var auth = mock(AuthService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, mock(LoginAttemptLimiter.class)))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"correo-privado-invalido\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").value(containsString("email=El correo no tiene un formato valido")))
                .andExpect(jsonPath("mensaje").value(containsString("password=La contrasena es obligatoria")))
                .andExpect(content().string(not(containsString("correo-privado-invalido"))))
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(auth);
    }

    @Test
    void validacionDeAltaConservaNombresDeCamposEnMensajeYNoEjecutaServicios() throws Exception {
        var clientes = mock(ClienteService.class);
        var registro = mock(ClienteRegistroService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, registro))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
        mvc.perform(post("/api/clientes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("codigo").value(2))
                .andExpect(jsonPath("mensaje").value(containsString("nombre=El nombre es obligatorio y no puede contener solo espacios.")))
                .andExpect(jsonPath("mensaje").value(containsString("correo=El correo electrónico es obligatorio.")))
                .andExpect(jsonPath("mensaje").value(containsString("telefonoMovil=El teléfono es obligatorio.")))
                .andExpect(jsonPath("mensaje").value(containsString("rfc=El RFC es obligatorio")))
                .andExpect(jsonPath("mensaje").value(containsString("curp=La CURP es obligatoria")))
                .andExpect(jsonPath("mensaje").value(containsString("domicilio=")))
                .andExpect(jsonPath("mensaje").value(containsString("contrasena=")));
        verifyNoInteractions(clientes, registro);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void altaYActualizacionDevuelvenLosMensajesObligatoriosExactos(String valor) throws Exception {
        var clientes = mock(ClienteService.class);
        var registro = mock(ClienteRegistroService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, registro))
                .setControllerAdvice(new ValidadorCampos(), new GlobalApiExceptionHandler()).build();
        var datos = new java.util.HashMap<String, Object>();
        datos.put("nombre", valor);
        datos.put("correo", valor);
        datos.put("telefonoMovil", valor);

        for (var request : List.of(postJson("/api/clientes", datos),
                json(org.springframework.http.HttpMethod.PUT, "/api/clientes", Map.of("id", 1, "datos", datos)))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").value(containsString("nombre=El nombre es obligatorio y no puede contener solo espacios.")))
                    .andExpect(jsonPath("mensaje").value(containsString("correo=El correo electrónico es obligatorio.")))
                    .andExpect(jsonPath("mensaje").value(containsString("telefonoMovil=El teléfono es obligatorio.")));
        }
        verifyNoInteractions(clientes, registro);
    }

    @Test
    void altaYActualizacionDevuelvenLosMensajesExactosDeFormato() throws Exception {
        var clientes = mock(ClienteService.class);
        var registro = mock(ClienteRegistroService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, registro))
                .setControllerAdvice(new ValidadorCampos(), new GlobalApiExceptionHandler()).build();
        var datos = Map.of("nombre", "Marco", "correo", "correo-invalido", "telefonoMovil", "123");

        for (var request : List.of(postJson("/api/clientes", datos),
                json(org.springframework.http.HttpMethod.PUT, "/api/clientes", Map.of("id", 1, "datos", datos)))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("mensaje").value(containsString("correo=El correo electrónico no tiene un formato válido.")))
                    .andExpect(jsonPath("mensaje").value(containsString("telefonoMovil=El teléfono debe contener exactamente 10 dígitos.")));
        }
        verifyNoInteractions(clientes, registro);
    }

    @Test void fallaAlAbrirOConfirmarTransaccionDevuelveJson503Seguro() throws Exception {
        for (RuntimeException error : List.of(new CannotCreateTransactionException("password=secreto"),
                new TransactionSystemException("jdbc://servidor-interno"),
                new DataAccessResourceFailureException("detalle sql"))) {
            verificar(error, 503, "No fue posible completar la operacion de clientes o cuentas");
        }
    }

    @Test void conflictoOptimistaDevuelve409ParaPoderReintentar() throws Exception {
        verificar(new OptimisticLockingFailureException("sql interno"), 409,
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez");
    }

    @Test void excepcionInesperadaNoExponeMensajesInternos() throws Exception {
        verificar(new IllegalStateException("token=secreto"), 500, "Ocurrio un error interno");
    }

    @Test void recursoInexistenteNoRevelaLosDatosDeLaConsulta() throws Exception {
        verificar(new ClienteNoEncontradoException("CURP=secreto"), 404,
                "No existe el cliente solicitado");
    }

    @Test void denegacionEnServicioNoSeConvierteEnErrorInterno() throws Exception {
        verificar(new AccessDeniedException("rol interno secreto"), 403,
                "No tienes permisos para realizar esta operacion");
    }

    @Test void integracionUsaElMismoFormatoSinRevelarCredencialesDelProveedor() throws Exception {
        for (ProductIntegrationErrorType type : ProductIntegrationErrorType.values()) {
            var catalogo = mock(ProductCatalogService.class);
            when(catalogo.obtenerProductos()).thenThrow(new ProductIntegrationException(type,
                    "https://interno/auth?password=secreto", new IllegalStateException("token=secreto")));
            var mvc = MockMvcBuilders.standaloneSetup(new ProductCatalogController(catalogo))
                    .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
            boolean proveedor = type == ProductIntegrationErrorType.GESTOPAGO_UNAVAILABLE;
            mvc.perform(get("/api/products")).andExpect(status().is(proveedor ? 502 : 500))
                    .andExpect(jsonPath("codigo").value(2))
                    .andExpect(jsonPath("timestamp").doesNotExist())
                    .andExpect(jsonPath("path").doesNotExist())
                    .andExpect(jsonPath("fieldErrors").doesNotExist())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(not(containsString("secreto"))))
                    .andExpect(content().string(not(containsString("https://interno"))));
        }
    }

    private void verificar(RuntimeException error, int status, String mensaje) throws Exception {
        var clientes = mock(ClienteService.class);
        when(clientes.consultar(any())).thenThrow(error);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, mock(ClienteRegistroService.class)))
                .setControllerAdvice(new ValidadorCampos(), new com.proyecto.servicios.controller.GlobalApiExceptionHandler()).build();
        mvc.perform(postJson("/api/clientes/consulta", Map.of("id", 1))).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("codigo").value(2)).andExpect(jsonPath("mensaje").value(mensaje))
                .andExpect(jsonPath("path").doesNotExist())
                .andExpect(jsonPath("timestamp").doesNotExist())
                .andExpect(jsonPath("fieldErrors").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("jdbc://"))))
                .andExpect(content().string(not(containsString("sql interno"))));
    }
}
