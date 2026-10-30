package com.proyecto.servicios.service;

import AES.AesEncryption;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AesEncryptionTest {
    // Datos sinteticos, no credenciales del proveedor.
    private final String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
    private final String iv = java.util.Base64.getEncoder().encodeToString(new byte[16]);

    @Test void cifraYDescifraUtf8ConLaInterfazSolicitada() {
        String payload = "{\"referencia\":\"Pago de teléfono México\",\"unidad\":\"083\"}";
        String encrypted = AesEncryption.AESCipher("ENCRYPT", payload, iv, key);
        assertThat(encrypted).isNotEqualTo(payload);
        assertThat(AesEncryption.AESCipher("DECRYPT", encrypted, iv, key)).isEqualTo(payload);
    }

    @Test void modoOConfiguracionInvalidosNoDevuelvenNull() {
        assertThatThrownBy(() -> AesEncryption.AESCipher("INVALID", "{}", iv, key))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesEncryption.AESCipher("ENCRYPT", "{}", iv, "AA=="))
                .isInstanceOf(IllegalStateException.class).hasMessage("No fue posible cifrar la peticion");
    }

    @Test void textoCifradoInvalidoFallaSinExponerlo() {
        assertThatThrownBy(() -> AesEncryption.AESCipher("DECRYPT", "contenido-invalido", iv, key))
                .isInstanceOf(IllegalStateException.class).hasMessage("No fue posible descifrar la respuesta")
                .hasNoCause();
    }
}
