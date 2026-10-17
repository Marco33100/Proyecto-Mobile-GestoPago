package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void altaYActualizacionUsanLosMensajesSolicitadosParaTextosObligatorios(String valor) {
        for (Class<?> dto : List.of(RegistrarClienteRequest.class, ActualizarClienteRequest.class)) {
            assertThat(validator.validateValue(dto, "nombre", valor)).extracting(ConstraintViolation::getMessage)
                    .contains("El nombre es obligatorio y no puede contener solo espacios.");
            assertThat(validator.validateValue(dto, "correo", valor)).extracting(ConstraintViolation::getMessage)
                    .contains("El correo electrónico es obligatorio.");
            assertThat(validator.validateValue(dto, "telefonoMovil", valor)).extracting(ConstraintViolation::getMessage)
                    .contains("El teléfono es obligatorio.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "uno@", "@example.com"})
    void altaYActualizacionUsanElMensajeSolicitadoParaCorreoInvalido(String valor) {
        for (Class<?> dto : List.of(RegistrarClienteRequest.class, ActualizarClienteRequest.class)) {
            assertThat(validator.validateValue(dto, "correo", valor)).extracting(ConstraintViolation::getMessage)
                    .contains("El correo electrónico no tiene un formato válido.");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "12345678901", "551234567A"})
    void altaYActualizacionUsanElMensajeSolicitadoParaTelefonoInvalido(String valor) {
        for (Class<?> dto : List.of(RegistrarClienteRequest.class, ActualizarClienteRequest.class)) {
            assertThat(validator.validateValue(dto, "telefonoMovil", valor)).extracting(ConstraintViolation::getMessage)
                    .contains("El teléfono debe contener exactamente 10 dígitos.");
        }
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

    @Test
    void rechazaNombreCorreoYContrasenaInvalidosEnElDtoActual() {
        var invalido = perfil("", null, "Morales", "Martinez", "correo-invalido", "weak");

        assertThat(validator.validate(invalido))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("nombre", "correo", "contrasena");
    }

    @Test
    void aceptaNombresCompuestosConLosEspaciosQueNormalizaElServicio() {
        var valido = perfil("  Marco  Antonio  ", "Jose", "De la Cruz", "Martinez",
                "marco@example.com", "Segura123!");

        assertThat(validator.validate(valido)).isEmpty();
    }

    @Test
    void rechazaEspaciosEnElCorreoDeRegistro() {
        var invalido = perfil("Marco", null, "Morales", "Martinez",
                " marco@example.com ", "Segura123!");

        assertThat(validator.validate(invalido))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("correo");
    }

    @Test
    void cadaParteDelNombreAdmiteCincuentaCaracteresYRechazaElExceso() {
        assertThat(validator.validate(perfil("A".repeat(50), "B".repeat(50),
                "C".repeat(50), "D".repeat(50), "marco@example.com", "Segura123!"))).isEmpty();

        assertThat(validator.validate(perfil("A".repeat(51), "B".repeat(51),
                "C".repeat(51), "D".repeat(51), "marco@example.com", "Segura123!")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("nombre", "segundoNombre", "apellidoPaterno", "apellidoMaterno");
    }

    @Test
    void elDtoDeRegistroNoExponeLaContrasenaEnToString() {
        var valido = perfil("Marco", null, "Morales", "Martinez",
                "marco@example.com", "Segura123!");

        assertThat(valido.toString()).doesNotContain(valido.contrasena(), valido.correo());
    }

    private RegistrarClienteRequest perfil(String nombre, String segundoNombre, String apellidoPaterno,
            String apellidoMaterno, String correo, String contrasena) {
        var original = request("MOMA900101HDFRRR01", "MOMA900101AB1", LocalDate.of(1990, 1, 1),
                "5512345678", new BigDecimal("1000.00"), "06000");
        return new RegistrarClienteRequest(nombre, segundoNombre, apellidoPaterno, apellidoMaterno,
                original.fechaNacimiento(), original.curp(), original.rfc(), original.sexo(),
                original.nacionalidad(), original.estadoCivil(), original.referenciaReconocimientoFacial(),
                correo, original.telefonoMovil(), original.telefonoAlternativo(), original.ocupacion(),
                original.empresa(), original.ingresoMensual(), original.domicilio(), contrasena);
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
