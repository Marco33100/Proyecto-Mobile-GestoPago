package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Construye cuerpos JSON reales; nunca transforma parametros de URL en datos del request. */
final class ApiTestRequests {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ApiTestRequests() { }

    static MockHttpServletRequestBuilder json(HttpMethod method, String path, Object body) throws Exception {
        return request(method, path).contentType("application/json").content(JSON.writeValueAsBytes(body));
    }

    static MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
        return json(HttpMethod.POST, path, body);
    }

    static MockHttpServletRequestBuilder cuentaConsulta(String numero) throws Exception {
        return postJson("/api/cuentas/consulta", Map.of("numeroCuenta", numero));
    }

    static MockHttpServletRequestBuilder saldoConsulta(String numero) throws Exception {
        return postJson("/api/cuentas/saldo/consulta", Map.of("numeroCuenta", numero));
    }

    static MockHttpServletRequestBuilder bajaCuenta(String numero) throws Exception {
        return json(HttpMethod.DELETE, "/api/cuentas", Map.of("numeroCuenta", numero));
    }

    static MockHttpServletRequestBuilder parcheCuenta(String numero, String cambios) {
        return request(HttpMethod.PATCH, "/api/cuentas").contentType("application/json")
                .content("{\"numeroCuenta\":\"" + numero + "\",\"cambios\":" + cambios + "}");
    }

    static MockHttpServletRequestBuilder usuarioConsulta(String nombre, String correo) throws Exception {
        return postJson("/api/usuarios/consulta", Map.of("nombre", nombre, "correo", correo));
    }

    static MockHttpServletRequestBuilder usuarioPassword(String nombre, String correo, String credenciales) throws Exception {
        return request(HttpMethod.PUT, "/api/usuarios/password").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("nombre", nombre, "correo", correo,
                        "credenciales", JSON.readTree(credenciales))));
    }
}
