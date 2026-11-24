package com.proyecto.servicios.model.cliente;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

class ContrasenaSeguraValidationTest {
    private record Credencial(@ContrasenaSegura String contrasena) {}

    @Test
    void rechazaVaciaDebilOFueraDe8a12() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new Credencial("Segura123"))).isNotEmpty();
            assertThat(validator.validate(new Credencial("Segura123 "))).isNotEmpty();
            for (String value : Arrays.asList(null, "", "        ", "Abc123", "soloMinusculas",
                    "SOLOMAYUS1", "SinNumeros!", "Ab1!" + "x".repeat(9), "Segura123\n")) {
                assertThat(validator.validate(new Credencial(value))).isNotEmpty();
            }
            assertThat(validator.validate(new Credencial("Segura123!"))).isEmpty();
            assertThat(validator.validate(new Credencial("Ab1!" + "x".repeat(4)))).isEmpty();
            assertThat(validator.validate(new Credencial("Ab1!" + "x".repeat(8)))).isEmpty();
        }
    }
}
