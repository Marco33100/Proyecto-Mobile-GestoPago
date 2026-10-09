package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrarClienteRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aceptaRegistroValido() {
        assertThat(validator.validate(request(
                "MOMA900101HDFRRR01", "MOMA900101AB1", LocalDate.of(1990, 1, 1),
                "5512345678", new BigDecimal("1000.00"), "06000"
        ))).isEmpty();
    }

    @Test
    void rechazaEdadMenorCurpRfcTelefonoIngresoYCodigoPostalInvalidos() {
        RegistrarClienteRequest request = request(
                "CURP", "RFC", LocalDate.now().minusYears(17),
                "12345", BigDecimal.ZERO, "1234"
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("curp", "rfc", "fechaNacimiento", "telefonoMovil", "ingresoMensual", "domicilio.codigoPostal");
    }

    @Test
    void rechazaNombresConNumerosOEspaciosSinLetras() {
        RegistrarClienteRequest original = request(
                "MOMA900101HDFRRR01", "MOMA900101AB1", LocalDate.of(1990, 1, 1),
                "5512345678", new BigDecimal("1000.00"), "06000"
        );
        RegistrarClienteRequest invalido = new RegistrarClienteRequest(
                "   ", original.segundoNombre(), "M0rales", original.apellidoMaterno(),
                original.fechaNacimiento(), original.curp(), original.rfc(), original.sexo(),
                original.nacionalidad(), original.estadoCivil(), original.referenciaReconocimientoFacial(),
                original.correo(), original.telefonoMovil(), original.telefonoAlternativo(),
                original.ocupacion(), original.empresa(), original.ingresoMensual(), original.domicilio(),
                original.contrasena()
        );

        assertThat(validator.validate(invalido))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nombre", "apellidoPaterno");
    }

    private RegistrarClienteRequest request(
            String curp, String rfc, LocalDate nacimiento,
            String telefono, BigDecimal ingreso, String codigoPostal
    ) {
        return new RegistrarClienteRequest(
                "Marco", null, "Morales", "Martinez", nacimiento,
                curp, rfc, Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO,
                null, "marco@example.com", telefono, null, "Ingeniero", "Empresa",
                ingreso,
                new ActualizarDomicilioRequest(
                        "Reforma", "100", null, "Centro", "Cuauhtemoc",
                        "Ciudad de Mexico", codigoPostal, "Mexico"
                ), "Segura123!"
        );
    }
}
