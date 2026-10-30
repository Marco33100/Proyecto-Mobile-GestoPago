package AES;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Cifrado del contrato de Gestopago; KEY e IV se reciben en Base64 desde configuracion. */
public final class AesEncryption {
    private AesEncryption() { }

    public static String AESCipher(String mode, String input, String IV, String KEY) {
        Mode operation;
        try {
            operation = Mode.valueOf(mode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("El modo debe ser ENCRYPT o DECRYPT");
        }
        byte[] iv = Base64.getDecoder().decode(IV);
        byte[] key = Base64.getDecoder().decode(KEY);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        SecretKey secretKey = new SecretKeySpec(key, 0, key.length, "AES");
        return operation == Mode.ENCRYPT ? encrypt(input, secretKey, ivspec) : decrypt(input, secretKey, ivspec);
    }

    public static String encrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No fue posible cifrar la peticion");
        }
    }

    public static String decrypt(String input, SecretKey secretKey, IvParameterSpec ivspec) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("No fue posible descifrar la respuesta");
        }
    }

    public enum Mode {
        ENCRYPT, DECRYPT
    }
}
