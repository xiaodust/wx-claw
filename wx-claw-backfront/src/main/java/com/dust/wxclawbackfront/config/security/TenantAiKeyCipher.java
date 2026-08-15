package com.dust.wxclawbackfront.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level encryption for tenant-supplied AI service API keys.
 *
 * <p>New values are stored as {@code enc:v1:<iv>:<ciphertext>}. Legacy plaintext
 * values are returned unchanged so existing rows can be migrated without an
 * immediate destructive schema change.</p>
 */
@Component
public class TenantAiKeyCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public TenantAiKeyCipher(
            @Value("${wxclaw.security.ai-key-encryption-key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("wxclaw.security.ai-key-encryption-key must be configured");
        }
        this.keySpec = deriveKey(configuredKey);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt tenant AI key", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            String payload = stored.substring(PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted value");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt tenant AI key", ex);
        }
    }

    private SecretKeySpec deriveKey(String configuredKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive tenant AI key encryption key", ex);
        }
    }
}
