package com.dust.wxclawbackfront.config.security;

import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Enforces a minimum password complexity policy.
 */
@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "密码不能为空", HttpStatus.BAD_REQUEST);
        }
        String value = password.trim();
        if (value.length() < 8 || value.length() > 64) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "密码长度需为 8-64 位", HttpStatus.BAD_REQUEST);
        }
        int categories = 0;
        if (value.chars().anyMatch(Character::isLowerCase)) categories++;
        if (value.chars().anyMatch(Character::isUpperCase)) categories++;
        if (value.chars().anyMatch(Character::isDigit)) categories++;
        if (value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) categories++;
        if (categories < 2) {
            throw new TenantRegistrationException("VALIDATION_ERROR",
                    "密码需包含字母、数字、大写字母、特殊字符中的至少两类", HttpStatus.BAD_REQUEST);
        }
    }
}
