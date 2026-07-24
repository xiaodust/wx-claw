package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSecretHasherTest {
    private final ApiSecretHasher hasher = new ApiSecretHasher();

    @Test
    void hashesSecretsWithRandomSalt() {
        String first = hasher.hash("tenant-secret");
        String second = hasher.hash("tenant-secret");

        assertNotEquals(first, second);
        assertTrue(hasher.matches("tenant-secret", first));
        assertFalse(hasher.matches("other-secret", first));
    }
}
