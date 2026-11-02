package edu.upb.semillero.scouting;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Field-level protection for prospect and communication data. Never log decrypted values. */
@Service
public class ScoutingCryptoService {
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec encryptionKey;
    private final byte[] fingerprintKey;
    private final ObjectMapper json;

    public ScoutingCryptoService(@Value("${semillero.scouting.encryption-secret:${semillero.jwt.secret}}") String secret,
            ObjectMapper json) {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.encryptionKey = new SecretKeySpec(key, "AES");
            this.fingerprintKey = key.clone();
            this.json = json;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo preparar el cifrado de scouting", e);
        }
    }

    public byte[] encrypt(Object value) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            java.security.SecureRandom.getInstanceStrong().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(value));
            return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudieron proteger los datos de scouting", e);
        }
    }

    public JsonNode decrypt(byte[] value) {
        if (value == null || value.length <= NONCE_BYTES) return json.createObjectNode();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            return json.readTree(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron descifrar datos de scouting; verifica SCOUTING_ENCRYPTION_SECRET", e);
        }
    }

    public String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular una huella de scouting", e);
        }
    }

    public String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
