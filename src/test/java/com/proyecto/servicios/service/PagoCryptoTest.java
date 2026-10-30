package com.proyecto.servicios.service;

import com.proyecto.servicios.service.Impl.*;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class PagoCryptoTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    private final String iv = Base64.getEncoder().encodeToString(new byte[16]);
    @Test void objetosDePagoNoExponenDatosSensiblesEnRepresentacionDeTexto() throws Exception {
        var id = java.util.UUID.randomUUID();
        var solicitud = new com.proyecto.servicios.model.pago.PagoDtos.PrepararRequest(
                id, "cuenta-privada", 1, 2, 1, new java.math.BigDecimal("10"), "5555555551", "referencia-privada");
        var respuesta = new PagoGateway.Respuesta(PagoGateway.Estado.APLICADO, "01", "123", "456",
                java.math.BigDecimal.TEN, null, "pin-privado", null, "evidencia-privada");
        var pago = new com.proyecto.servicios.model.pago.PagoDtos.PagoResponse(id, "APLICADO", "UPC1234567",
                "cuenta-privada", "producto", java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO,
                null, "123", "456", "pin-privado", null, null, "5555555551", "referencia-privada",
                null, null, null, false);
        for (Object objeto : java.util.List.of(solicitud, respuesta, pago,
                new PagoLedger.Envio(id, "payload-privado"), new PagoLedger.Confirmacion(id, "payload-privado", id),
                new PagoLedger.Preparacion(pago, false, "payload-privado"))) {
            assertThat(objeto.toString()).doesNotContain("privad", "5555555551");
        }
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(json.writeValueAsString(pago)).contains("pin-privado", "referencia-privada");
    }
    @Test void cifradoEsCompatibleConContratoAes256Cbc() throws Exception {
        var crypto = new PagoCrypto(key, iv);
        String payload = "{\"unidad\":\"083\",\"referencia\":\"PAGO-123\"}";
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(new byte[32], "AES"), new IvParameterSpec(new byte[16]));
        assertThat(new String(cipher.doFinal(Base64.getDecoder().decode(crypto.cifrarProveedor(payload))), StandardCharsets.UTF_8)).isEqualTo(payload);
    }
    @Test void auditoriaUsaNonceDistintoYDetectaAlteraciones() {
        var crypto = new PagoCrypto(key, iv);
        String a = crypto.proteger("referencia sensible"), b = crypto.proteger("referencia sensible");
        assertThat(a).isNotEqualTo(b).doesNotContain("sensible");
        assertThat(crypto.revelar(a)).isEqualTo("referencia sensible");
        byte[] bytes = Base64.getDecoder().decode(a); bytes[15] ^= 1;
        assertThatThrownBy(() -> crypto.revelar(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PagoCrypto("", "").validarConfiguracion()).isInstanceOf(com.proyecto.servicios.exception.PagoException.class);
    }
    @Test void upcTieneDiezCaracteresAlfanumericos() {
        var valores = new java.util.HashSet<String>();
        for (int i = 0; i < 1000; i++) assertThat(valores.add(PagoCrypto.upc())).isTrue();
        assertThat(valores).allMatch(v -> v.matches("[A-Z0-9]{10}"));
    }
    @Test void interpretaCodigosPorMetodoYNoSoloPorHttp200() {
        String aplicado = "<RESPONSE><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION><MONTO>1E+2</MONTO><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE>";
        var result = GestopagoPagoGateway.interpretar(aplicado, false);
        assertThat(result.estado()).isEqualTo(PagoGateway.Estado.APLICADO);
        assertThat(result.monto()).isEqualByComparingTo("100");
        assertThat(GestopagoPagoGateway.interpretar(aplicado.replace("01", "06"), true).estado()).isEqualTo(PagoGateway.Estado.APLICADO);
        assertThat(GestopagoPagoGateway.interpretar(aplicado.replace("01", "06"), false).estado()).isEqualTo(PagoGateway.Estado.INCIERTO);
        assertThat(GestopagoPagoGateway.interpretar(aplicado.replace("01", "70"), true).estado()).isEqualTo(PagoGateway.Estado.RECHAZADO);
        assertThat(GestopagoPagoGateway.interpretar(aplicado.replace("01", "82"), false).estado()).isEqualTo(PagoGateway.Estado.INCIERTO);
        assertThat(GestopagoPagoGateway.interpretar("<RESPONSE><CODIGO>01</CODIGO></RESPONSE>", false).estado()).isEqualTo(PagoGateway.Estado.INCIERTO);
    }
    @Test void rechazaXmlExternoYRespuestasMalformadas() {
        String xxe = "<!DOCTYPE RESPONSE [<!ENTITY x SYSTEM 'file:///nonexistent'>]><RESPONSE><CODIGO>&x;</CODIGO></RESPONSE>";
        assertThat(GestopagoPagoGateway.interpretar(xxe, false).estado()).isEqualTo(PagoGateway.Estado.INCIERTO);
        assertThat(GestopagoPagoGateway.interpretar("<html>Error</html>", false).estado()).isEqualTo(PagoGateway.Estado.INCIERTO);
    }
    @Test void referenciaConservaDeudaCeroYSaldoAFavorSinConfundirConSaldoBancario() {
        for (String monto : java.util.List.of("1034.5", "1034.5000", "0", "-150.00", "1E+2", "99999999999999999.99")) {
            var resultado = GestopagoPagoGateway.interpretarReferencia(
                    "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><SALDO>" + monto + "</SALDO></MENSAJE></RESPONSE>");
            assertThat(resultado.estado()).isEqualTo(PagoGateway.EstadoReferencia.VALIDA);
            assertThat(resultado.saldo()).isEqualByComparingTo(monto);
            assertThat(resultado.toString()).isEqualTo("Referencia[protegida]");
        }
        assertThat(GestopagoPagoGateway.interpretarReferencia("<RESPONSE><CODIGO>02</CODIGO></RESPONSE>").estado())
                .isEqualTo(PagoGateway.EstadoReferencia.INVALIDA);
    }
    @Test void referenciaSinDeudaVerificableOXmlHostilEsIncierta() {
        for (String contenido : java.util.List.of("", "<html>Error</html>",
                "<RESPONSE><CODIGO>01</CODIGO></RESPONSE>",
                "<RESPONSE><CODIGO>01</CODIGO><SALDO>NaN</SALDO></RESPONSE>",
                "<RESPONSE><CODIGO>01</CODIGO><SALDO>1.001</SALDO></RESPONSE>",
                "<RESPONSE><CODIGO>01</CODIGO><SALDO>1</SALDO><SALDO>2</SALDO></RESPONSE>",
                "<RESPONSE><CODIGO>01</CODIGO><SALDO>1E+1000000</SALDO></RESPONSE>",
                "<!DOCTYPE RESPONSE [<!ENTITY x SYSTEM 'file:///nonexistent'>]><RESPONSE><CODIGO>01</CODIGO><SALDO>&x;</SALDO></RESPONSE>"))
            assertThat(GestopagoPagoGateway.interpretarReferencia(contenido).estado()).isEqualTo(PagoGateway.EstadoReferencia.INCIERTA);
    }
}
