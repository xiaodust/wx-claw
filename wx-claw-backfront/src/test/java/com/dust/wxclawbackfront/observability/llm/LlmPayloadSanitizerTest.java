package com.dust.wxclawbackfront.observability.llm;

import com.dust.wxclawbackfront.observability.llm.service.LlmPayloadSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPayloadSanitizerTest {
    @Test
    void removesSecretsAndBase64Media() {
        LlmPayloadSanitizer sanitizer = new LlmPayloadSanitizer(4096);
        String payload = "{\"api_key\":\"secret-value\",\"url\":\"https://x.test?a=1&token=abc\"," +
                "\"image\":\"data:image/png;base64," + "A".repeat(256) + "\"}";

        String sanitized = sanitizer.sanitize(payload).value();

        assertFalse(sanitized.contains("secret-value"));
        assertFalse(sanitized.contains("token=abc"));
        assertFalse(sanitized.contains("A".repeat(128)));
        assertTrue(sanitized.contains("***"));
        assertTrue(sanitized.contains("base64-media-removed"));
    }

    @Test
    void marksOversizedPayloadAsTruncated() {
        LlmPayloadSanitizer.SanitizedPayload result = new LlmPayloadSanitizer(1024)
                .sanitize("x".repeat(2048));

        assertTrue(result.truncated());
        assertTrue(result.value().contains("payload truncated"));
        assertTrue(result.sha256().length() == 64);
    }
}
