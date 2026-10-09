package com.proyecto.servicios.model.cliente;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Fecha de calendario: no convertir timestamps, numeros o arrays a una fecha. */
public final class FechaSinHoraDeserializer extends JsonDeserializer<LocalDate> {
    private static final Pattern FORMATO = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(LocalDate.class, "La fecha debe ser un texto AAAA-MM-DD");
        }
        String value = parser.getText();
        if (!FORMATO.matcher(value).matches()) {
            return context.reportInputMismatch(LocalDate.class, "La fecha debe usar AAAA-MM-DD, sin hora");
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(LocalDate.class, "La fecha de calendario no es valida");
        }
    }
}
