package com.dust.wxclawbackfront.observability.llm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class LlmPayloadSanitizer {
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(?i)(\\\"(?:authorization|api[_-]?key|access[_-]?token|secret|cookie)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|access[_-]?token|token|secret)=)[^&\\s\\\"]+");
    private static final Pattern DATA_URL = Pattern.compile(
            "data:([^;,\\s]+)?(?:;[^,\\s]+)*;base64,[A-Za-z0-9+/=\\r\\n]{128,}");

    private final int maxPayloadChars;

    public LlmPayloadSanitizer(@Value("${wxclaw.admin.audit.max-payload-chars:2097152}") int maxPayloadChars) {
        this.maxPayloadChars = Math.max(1024, maxPayloadChars);
    }

    public SanitizedPayload sanitize(String payload) {
        if (payload == null) {
            return new SanitizedPayload(null, false, 0, null);
        }
        String sanitized = SENSITIVE_JSON.matcher(payload).replaceAll("$1***$2");
        sanitized = SENSITIVE_QUERY.matcher(sanitized).replaceAll("$1***");
        sanitized = DATA_URL.matcher(sanitized).replaceAll("[base64-media-removed]");
        int originalLength = sanitized.length();
        String sha256 = sha256(sanitized);
        if (originalLength <= maxPayloadChars) {
            return new SanitizedPayload(sanitized, false, originalLength, sha256);
        }
        int half = maxPayloadChars / 2;
        String truncated = sanitized.substring(0, half)
                + "\n...[payload truncated, originalLength=" + originalLength + "]...\n"
                + sanitized.substring(originalLength - half);
        return new SanitizedPayload(truncated, true, originalLength, sha256);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record SanitizedPayload(String value, boolean truncated, int originalLength, String sha256) {
    }
}
