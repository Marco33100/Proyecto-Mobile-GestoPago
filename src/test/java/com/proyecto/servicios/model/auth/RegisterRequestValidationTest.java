package com.proyecto.servicios.model.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMalformedRegistrationPayload() {
        RegisterRequest request = new RegisterRequest(
                "not-an-email", "a!", "weak", ""
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "identifier", "password", "fullName");
    }

    @Test
    void acceptsANameWithSingleSpaces() {
        RegisterRequest request = new RegisterRequest(
                "marco@example.com", "marco_01", "Password1", "Marco Antonio Martinez"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsWhitespaceOnlyAndRepeatedOrOuterSpacesInName() {
        assertThat(validator.validate(validRequest("   "))).isNotEmpty();
        assertThat(validator.validate(validRequest(" Marco Martinez"))).isNotEmpty();
        assertThat(validator.validate(validRequest("Marco Martinez "))).isNotEmpty();
        assertThat(validator.validate(validRequest("Marco  Martinez"))).isNotEmpty();
        assertThat(validator.validate(validRequest("Marco     Martinez"))).isNotEmpty();
    }

    @Test
    void rejectsSpacesInEmailAndIdentifier() {
        RegisterRequest spacedEmail = new RegisterRequest(
                " marco@example.com ", "marco_01", "Password1", "Marco Martinez"
        );
        RegisterRequest spacedIdentifier = new RegisterRequest(
                "marco@example.com", " marco_01 ", "Password1", "Marco Martinez"
        );

        assertThat(validator.validate(spacedEmail)).isNotEmpty();
        assertThat(validator.validate(spacedIdentifier)).isNotEmpty();
    }

    private RegisterRequest validRequest(String fullName) {
        return new RegisterRequest(
                "marco@example.com", "marco_01", "Password1", fullName
        );
    }
}
