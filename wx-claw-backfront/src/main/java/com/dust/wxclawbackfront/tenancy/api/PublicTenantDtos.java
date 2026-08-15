package com.dust.wxclawbackfront.tenancy.api;

import java.time.LocalDateTime;

/**
 * 租户自助注册（公开 API）的请求/响应契约。
 *
 * <p>注册是租户进入系统的唯一自助入口：控制台使用用户名密码登录，
 * 注册不再签发 API Key（API Key 仅由 Bootstrap/管理端凭据体系负责）。</p>
 */
public final class PublicTenantDtos {
    private PublicTenantDtos() {
    }

    public record RegisterTenantRequest(String tenantName, String tenantCode, String contactEmail,
                                        String username, String password, String inviteCode,
                                        String emailCode) {
    }

    public record EmailCodeRequest(String email, String purpose) {
    }

    public record RegisteredTenant(String tenantId, String tenantCode, String tenantName, String status,
                                   LocalDateTime createdAt, String username, String sessionToken,
                                   LocalDateTime sessionExpiresAt) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record ForgotPasswordRequest(String usernameOrEmail) {
    }

    public record ResetPasswordRequest(String token, String newPassword) {
    }

    public record OperationResult(String message) {
    }

    public record AuthResult(String sessionToken, LocalDateTime expiresAt,
                             String tenantId, String tenantCode, String tenantName) {
    }

    /** 与现有 REST API 一致的结构化错误体：{error, message}。 */
    public record ApiError(String error, String message) {
    }
}
