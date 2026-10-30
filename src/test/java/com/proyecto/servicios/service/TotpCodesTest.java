package com.proyecto.servicios.service;

import com.proyecto.servicios.service.Impl.TotpCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class TotpCodesTest {
    // Clave publica de los vectores RFC 6238, nunca una credencial real.
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @ParameterizedTest
    @CsvSource({"59,94287082", "1111111109,07081804", "1111111111,14050471", "1234567890,89005924",
            "2000000000,69279037", "20000000000,65353130"})
    void coincideConVectoresOficiales(long seconds, String expected) {
        var codes = new TotpCodes(Clock.fixed(Instant.ofEpochSecond(seconds), ZoneOffset.UTC));
        assertThat(codes.hotp(SECRET, codes.currentStep(), 8)).isEqualTo(expected);
        assertThat(codes.code(SECRET, codes.currentStep())).isEqualTo(expected.substring(2));
    }
    @Test
    void admiteDesfaseLimitadoPeroNoReplayNiFormatosInvalidos() {
        var codes = new TotpCodes(Clock.fixed(Instant.ofEpochSecond(3000), ZoneOffset.UTC));
        String current = codes.code(SECRET, 100);
        assertThat(codes.matchingStep(SECRET, current, -1)).isEqualTo(100);
        assertThat(codes.matchingStep(SECRET, current, 100)).isEqualTo(-1);
        assertThat(codes.matchingStep(SECRET, codes.code(SECRET, 99), -1)).isEqualTo(99);
        assertThat(codes.matchingStep(SECRET, codes.code(SECRET, 101), -1)).isEqualTo(101);
        assertThat(codes.matchingStep(SECRET, codes.code(SECRET, 98), -1)).isEqualTo(-1);
        for (String invalid : new String[]{"", "true", "12345", "1234567", " 123456", "12a456"})
            assertThat(codes.matchingStep(SECRET, invalid, -1)).isEqualTo(-1);
    }
    @Test
    void secretosAleatoriosTienen160BitsEnBase32() {
        var codes = new TotpCodes(); var values = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) assertThat(values.add(codes.newSecret())).isTrue();
        assertThat(values).allMatch(secret -> secret.matches("[A-Z2-7]{32}"));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void generadorPowerShellCoincideConTotpSinEnviarSecretosPorRed() throws Exception {
        String fixture = "function global:Read-Host { ConvertTo-SecureString '" + SECRET
                + "' -AsPlainText -Force }; & './scripts/Get-LocalTotp.ps1'";
        var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", fixture)
                .redirectErrorStream(true).start();
        assertThat(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
        var match = java.util.regex.Pattern.compile("Codigo: ([0-9]{6})").matcher(output);
        assertThat(match.find()).as(output).isTrue();
        assertThat(new TotpCodes().matchingStep(SECRET, match.group(1), -1)).isGreaterThanOrEqualTo(0);
    }
}
