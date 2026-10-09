package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class FechaNacimientoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void altaYActualizacionAceptanFechaRealSinHora() throws Exception {
        String json = "{\"fechaNacimiento\":\"2000-02-29\"}";
        assertThat(mapper.readValue(json, RegistrarClienteRequest.class).fechaNacimiento())
                .isEqualTo(LocalDate.of(2000, 2, 29));
        assertThat(mapper.readValue(json, ActualizarClienteRequest.class).fechaNacimiento())
                .isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1990-01-01T12:30:00\"", "\"1990-01-01T12:30:00Z\"",
            "\"1990-01-01T12:30:00-06:00\"", "\"1990-01-01 00:00:00\"",
            "\"1990-02-30\"", "\"1900-02-29\"", "\"1990-1-1\"", "\" 1990-01-01 \"",
            "\"01/01/1990\"", "\"\"", "[1990,1,1]", "123456789", "{\"year\":1990}"})
    void noRecortaHorasNiAceptaFechasInvalidasORepresentacionesAlternativas(String value) {
        String json = "{\"fechaNacimiento\":" + value + "}";
        assertThatExceptionOfType(JsonMappingException.class)
                .isThrownBy(() -> mapper.readValue(json, RegistrarClienteRequest.class));
        assertThatExceptionOfType(JsonMappingException.class)
                .isThrownBy(() -> mapper.readValue(json, ActualizarClienteRequest.class));
    }

    @Test
    void respuestaSiempreSerializaNacimientoComoTextoSinHora() throws Exception {
        // El resto del perfil no importa para comprobar el contrato de esta propiedad.
        var response = new ClienteResponse(1L, "Marco", null, "Martinez", "Lopez",
                LocalDate.of(1990, 1, 1), null, null, null, null, null, null, null, null, null,
                null, null, null, true, null, null, null, null);
        var json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.get("fechaNacimiento").isTextual()).isTrue();
        assertThat(json.get("fechaNacimiento").asText()).isEqualTo("1990-01-01");
    }
}
