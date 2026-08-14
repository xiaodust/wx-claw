package com.dust.wxclawbackfront.tenancy.api;

import java.time.LocalDateTime;

/**
 * 租户自助注册（公开 API）的请求/响应契约。
 *
 * <p>注册是租户进入系统的唯一自助入口，返回的 apiKey 只在注册成功时展示一次，
 * 数据库仅保存其 PBKDF2 哈希，响应之后服务端无法再还原原始 Key。</p>
 */
public final class PublicTenantDtos {
    private PublicTenantDtos() {
    }

    public record RegisterTenantRequest(String tenantName, String tenantCode, String contactEmail) {
    }

    public record RegisteredTenant(String tenantId, String tenantCode, String tenantName, String status,
                                   LocalDateTime createdAt, String credentialId, String apiKey, String scopes) {
    }

    /** 与现有 REST API 一致的结构化错误体：{error, message}。 */
    public record ApiError(String error, String message) {
    }
}
