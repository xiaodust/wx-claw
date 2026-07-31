package com.dust.wxclawbackfront.tenancy.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 密钥的单向 PBKDF2 哈希组件。
 *
 * <p>数据库仅保存算法、迭代次数、随机盐和派生值，不保存原始密钥；校验时使用
 * {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间比较，降低时序攻击风险。</p>
 */
@Component
public class ApiSecretHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;

    public String hash(String secret) {
        // 每个凭据使用独立随机盐，相同密钥也会得到不同哈希。
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2_sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(derive(secret, salt, ITERATIONS));
    }

    public boolean matches(String secret, String encoded) {
        if (secret == null || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        try {
            // 迭代次数随哈希保存，后续提高默认强度时仍可验证历史凭据。
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(secret, salt, iterations));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] derive(String secret, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash API secret", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
