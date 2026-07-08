package com.dust.wxclawbackfront.ilnk.media;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public final class WechatCdnCrypto {

    private WechatCdnCrypto() {
    }

    public static byte[] decryptAes128EcbPkcs7(byte[] encryptedBytes, String aesKeyBase64OrHex) {
        if (encryptedBytes == null || encryptedBytes.length == 0) {
            throw new IllegalArgumentException("encryptedBytes is empty");
        }
        byte[] key = decodeAesKey(aesKeyBase64OrHex);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(encryptedBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("decrypt failed: " + ex.getMessage(), ex);
        }
    }

    private static byte[] decodeAesKey(String aesKeyBase64OrHex) {
        if (aesKeyBase64OrHex == null || aesKeyBase64OrHex.isBlank()) {
            throw new IllegalArgumentException("aesKey is blank");
        }
        String trimmed = aesKeyBase64OrHex.trim();
        if (isHexKey(trimmed)) {
            return hexToBytes(trimmed);
        }

        byte[] decoded = Base64.getDecoder().decode(trimmed);
        if (decoded.length == 16) {
            return decoded;
        }

        String maybeHex = new String(decoded).trim();
        if (isHexKey(maybeHex)) {
            return hexToBytes(maybeHex);
        }

        throw new IllegalArgumentException("unsupported aesKey format");
    }

    private static boolean isHexKey(String value) {
        if (value == null || value.length() != 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
