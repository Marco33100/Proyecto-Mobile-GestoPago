package com.proyecto.servicios.service;

import AES.AesEncryption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class GestopagoPostmanPayloadTest {
    @TempDir Path directory;

    @Test void scriptPreparaSinRedYCifradoEsCompatibleConJava() throws Exception {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        String iv = Base64.getEncoder().encodeToString(new byte[16]);
        Path properties = directory.resolve("fixture.properties");
        Files.writeString(properties, "gestopago.crypto.secret-key=" + key + "\ngestopago.crypto.iv=" + iv);
        Path output = directory.resolve("prepared.json");
        Path log = directory.resolve("script.log");
        var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-File",
                Path.of("scripts/New-GestopagoPostmanPayload.ps1").toAbsolutePath().toString(),
                "-IdProducto", "1", "-IdServicio", "2", "-Telefono", "5555555551",
                "-PropertiesPath", properties.toString(), "-Salida", output.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertThat(completed).isTrue();
        assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        var mapper = new ObjectMapper();
        var result = mapper.readTree(Files.readString(output));
        String decrypted = AesEncryption.AESCipher("DECRYPT", result.path("signed").asText(), iv, key);
        assertThat(mapper.readTree(decrypted)).isEqualTo(result.path("payload"));
        assertThat(result.path("payload").path("upc").asText()).matches("[A-Z0-9]{10}");
        assertThat(result.path("payload").path("horaLocal").asText()).matches("[0-9]{14}");
        assertThat(result.path("payload").path("unidad").asText()).isEqualTo("083");
        assertThat(Files.readString(log)).doesNotContain(key, iv, result.path("signed").asText());
    }
}
