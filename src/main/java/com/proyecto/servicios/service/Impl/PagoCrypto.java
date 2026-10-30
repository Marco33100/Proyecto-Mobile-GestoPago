package com.proyecto.servicios.service.Impl;

import AES.AesEncryption;
import com.proyecto.servicios.exception.PagoException;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class PagoCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final String claveBase64;
    private final String ivBase64;
    public PagoCrypto(@Value("${gestopago.crypto.secret-key:}") String clave,
                      @Value("${gestopago.crypto.iv:}") String iv) { claveBase64 = clave; ivBase64 = iv; }

    public void validarConfiguracion() {
        try {
            if (Base64.getDecoder().decode(claveBase64).length != 32 || Base64.getDecoder().decode(ivBase64).length != 16) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new PagoException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no Disponible"); }
    }

    public String cifrarProveedor(String json) {
        validarConfiguracion();
        return AesEncryption.AESCipher(AesEncryption.Mode.ENCRYPT.name(), json, ivBase64, claveBase64);
    }

    // Authenticated at-rest encryption with a purpose-derived key and a fresh nonce, not the provider's fixed CBC IV.
    private SecretKeySpec claveAuditoria() throws GeneralSecurityException {
        validarConfiguracion();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(claveBase64), "HmacSHA256"));
        return new SecretKeySpec(mac.doFinal("gestopago/payment-audit/v1".getBytes(StandardCharsets.UTF_8)), "AES");
    }
    public String proteger(String texto) {
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveAuditoria(), new GCMParameterSpec(128, nonce));
            byte[] cuerpo = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + cuerpo.length).put(nonce).put(cuerpo).array());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("No fue posible proteger el registro"); }
    }
    public String revelar(String texto) {
        try {
            byte[] bytes = Base64.getDecoder().decode(texto);
            if (bytes.length < 28) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveAuditoria(), new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new IllegalStateException("Registro de pago no verificable"); }
    }
    public static String nonce() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String upc() {
        return RandomStringUtils.random(10, 0, ALFABETO.length, false, false, ALFABETO, RANDOM);
    }
    public static String huella(String texto) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
