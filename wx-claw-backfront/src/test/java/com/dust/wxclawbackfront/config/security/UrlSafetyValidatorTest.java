package com.dust.wxclawbackfront.config.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator(false);

    @Test
    void rejectsNonHttpsCustomBaseUrl() {
        assertThatThrownBy(() -> validator.validateCustomBaseUrl("http://example.com/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsIpLiteralCustomBaseUrl() {
        assertThatThrownBy(() -> validator.validateCustomBaseUrl("https://127.0.0.1/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IP");
    }

    @Test
    void rejectsNonHttpFetchUrl() {
        assertThatThrownBy(() -> validator.validatePublicFetchUrl("ftp://example.com/file.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }
}
