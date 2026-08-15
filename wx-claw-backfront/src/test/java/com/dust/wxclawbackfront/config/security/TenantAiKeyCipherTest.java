package com.dust.wxclawbackfront.config.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAiKeyCipherTest {

    private final TenantAiKeyCipher cipher = new TenantAiKeyCipher("test-encryption-key");

    @Test
    void roundTripsEncryptedValue() {
        String encrypted = cipher.encrypt("sk-secret-1234");

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).isNotEqualTo("sk-secret-1234");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-secret-1234");
    }

    @Test
    void leavesLegacyPlaintextUnchanged() {
        assertThat(cipher.decrypt("sk-legacy-1234")).isEqualTo("sk-legacy-1234");
    }
}
