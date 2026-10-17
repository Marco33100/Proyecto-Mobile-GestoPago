package com.proyecto.servicios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(mapper);

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 503})
    void conservaElContratoYLasCabecerasSinExponerLaExcepcion(int status) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/cuentas/activas");
        var response = new MockHttpServletResponse();
        switch (status) {
            case 401 -> entryPoint.commence(request, response,
                    new BadCredentialsException("password=fixture-secret-in-cause"));
            case 403 -> entryPoint.forbidden(request, response);
            case 429 -> entryPoint.tooManyRequests(request, response, 30);
            case 503 -> entryPoint.serviceUnavailable(request, response);
            default -> throw new AssertionError("Estado no contemplado en la prueba");
        }

        var messages = Map.of(
                401, "La sesion es invalida, expiro o fue reemplazada por un nuevo inicio de sesion",
                403, "No tienes permisos para realizar esta operacion",
                429, "Demasiados intentos de inicio de sesion. Espera antes de intentar nuevamente.",
                503, "El servicio de autenticacion no esta disponible");
        var body = mapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Retry-After")).isEqualTo(status == 429 ? "30" : null);
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("codigo").asInt()).isEqualTo(2);
        assertThat(body.path("mensaje").asText()).isEqualTo(messages.get(status));
        assertThat(response.getContentAsString()).doesNotContain("fixture-secret", "BadCredentialsException");
    }
}
