package com.proyecto.servicios.support;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class ClienteDatosTest {
    @Test
    void normalizaSinCambiarReglasDeCamposOpcionales() {
        assertThat(ClienteDatos.normalizarTexto("  Marco     Martinez\t ")).isEqualTo("Marco Martinez");
        assertThat(ClienteDatos.normalizarOpcional(null)).isNull();
        assertThat(ClienteDatos.normalizarOpcional("   ")).isNull();
        assertThat(ClienteDatos.normalizarMayusculas(" abc ")).isEqualTo("ABC");
        assertThat(ClienteDatos.normalizarCorreo(" MARCO@example.com ")).isEqualTo("marco@example.com");
        assertThatThrownBy(() -> ClienteDatos.validarNombres("   ", "Martinez", "Lopez"))
                .isInstanceOf(ClienteValidationException.class);
    }

    @Test
    void segundoNombreOpcionalPeroDeTresLetras() {
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarSegundoNombre(null));
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarSegundoNombre("  "));
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarSegundoNombre("Ana"));
        assertThatThrownBy(() -> ClienteDatos.validarSegundoNombre(" An "))
                .isInstanceOf(ClienteValidationException.class);
    }

    @Test
    void ingresoSoloOpcionalSinEmpresaYConOcupacionSinIngreso() {
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarIngreso("Desempleado(a)", null, null));
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarIngreso("Estudiante", " ", null));
        assertThatNoException().isThrownBy(() -> ClienteDatos.validarIngreso("Docente", null, java.math.BigDecimal.TEN));
        assertThatThrownBy(() -> ClienteDatos.validarIngreso("Docente", null, null))
                .isInstanceOf(ClienteValidationException.class);
        assertThatThrownBy(() -> ClienteDatos.validarIngreso("Estudiante", "Cafeteria", null))
                .isInstanceOf(ClienteValidationException.class);
    }

    @Test
    void corteDeEdadIncluyeCumpleanosYConservaReglaDeAnioBisiesto() {
        LocalDate hoy = LocalDate.of(2026, 2, 28);
        assertThat(ClienteDatos.esMayorDeEdad(LocalDate.of(2008, 2, 28), hoy)).isTrue();
        assertThat(ClienteDatos.esMayorDeEdad(LocalDate.of(2008, 2, 29), hoy)).isFalse();
        assertThat(ClienteDatos.esMayorDeEdad(null, hoy)).isFalse();
    }
}
