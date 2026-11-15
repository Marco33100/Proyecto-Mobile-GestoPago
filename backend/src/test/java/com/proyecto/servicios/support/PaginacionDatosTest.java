package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacionDatosTest {
    @ParameterizedTest
    @CsvSource({"0, 1", "0, 100", "1, 20", "2147483647, 100"})
    void aceptaLosLimitesDelContratoActual(int pagina, int tamanio) {
        assertThatCode(() -> PaginacionDatos.validar(pagina, tamanio)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"-1, 10", "0, 0", "0, -1", "0, 101", "-2147483648, 1", "1, 2147483647"})
    void rechazaValoresFueraDelContratoConElMismoErrorDeNegocio(int pagina, int tamanio) {
        assertThatThrownBy(() -> PaginacionDatos.validar(pagina, tamanio))
                .isInstanceOf(ClienteValidationException.class)
                .hasMessage("Pagina o tamanio fuera del rango permitido");
    }
}
