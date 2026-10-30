package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.service.Impl.GestoPagoTokenCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class GestoPagoTokenCipherTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    private final GestoPagoTokenCipher cipher = new GestoPagoTokenCipher(key, "");

    @Test
    void cifraConNonceDistintoYVerificaIdentidadDelRegistro() {
        String first = cipher.encrypt("secret-token", 123, "device");
        assertThat(first).startsWith("gp-token:v1:").doesNotContain("secret-token")
                .isNotEqualTo(cipher.encrypt("secret-token", 123, "device"));
        assertThat(cipher.decrypt(first, 123, "device")).isEqualTo("secret-token");
        assertThatThrownBy(() -> cipher.decrypt(first, 124, "device")).hasNoCause();
        assertThatThrownBy(() -> cipher.decrypt(first, 123, "other")).hasNoCause();
    }

    @Test
    void rechazaManipulacionTextoPlanoYClaveDesconocida() {
        String encrypted = cipher.encrypt("secret-token", 123, "device");
        int offset = encrypted.lastIndexOf(':') + 1;
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(offset));
        bytes[15] ^= 1;
        String tampered = encrypted.substring(0, offset) + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(tampered, 123, "device"))
                .hasMessageNotContaining("secret-token").hasNoCause();
        assertThatThrownBy(() -> cipher.decrypt("secret-token", 123, "device")).hasNoCause();
        byte[] other = new byte[32];
        other[0] = 1;
        var wrong = new GestoPagoTokenCipher(Base64.getEncoder().encodeToString(other), "");
        assertThatThrownBy(() -> wrong.decrypt(encrypted, 123, "device")).hasNoCause();
    }

    @Test
    void exigeClaveConfiguradaDe256Bits() {
        for (String invalid : new String[]{"", "not-base64", Base64.getEncoder().encodeToString(new byte[16])}) {
            assertThatThrownBy(() -> new GestoPagoTokenCipher(invalid, ""))
                    .isInstanceOf(IllegalStateException.class).hasNoCause();
        }
    }

    @Test
    void admiteClaveAnteriorParaRotacion() {
        String encrypted = cipher.encrypt("secret-token", 123, "device");
        byte[] next = new byte[32];
        next[0] = 2;
        var rotated = new GestoPagoTokenCipher(Base64.getEncoder().encodeToString(next), key);
        assertThat(rotated.decrypt(encrypted, 123, "device")).isEqualTo("secret-token");
        assertThat(rotated.needsRotation(encrypted)).isTrue();
        assertThat(rotated.needsRotation(rotated.encrypt("secret-token", 123, "device"))).isFalse();
    }

    @Test
    void deserializaTokenDelProveedorPeroNoLoSerializaNiIncluyeEnToString() throws Exception {
        var mapper = new ObjectMapper();
        var response = mapper.readValue("{\"token\":\"secret-token\",\"success\":true}", GestoPagoAuthResponse.class);
        assertThat(response.getToken()).isEqualTo("secret-token");
        assertThat(mapper.writeValueAsString(response)).doesNotContain("secret-token", "\"token\"");
        assertThat(response.toString()).doesNotContain("secret-token");
        var entity = new GestoPagoToken();
        entity.setToken("secret-token");
        assertThat(mapper.writeValueAsString(entity)).doesNotContain("secret-token", "\"token\"");
        assertThat(entity.toString()).doesNotContain("secret-token");
    }
}
