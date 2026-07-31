package com.dust.wxclawbackfront.tenancy.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户调用 REST API 使用的凭据。
 * 原始 Key 仅在创建时展示，数据库只持久化 PBKDF2 哈希和授权 Scope。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tenant_api_credential")
public class TenantApiCredential extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** API Key 中点号前的公开定位部分。 */
    @Column(name = "credential_id", nullable = false, unique = true, updatable = false, length = 64)
    private String credentialId;

    /** API Key 中 secret 部分的 PBKDF2 编码值，不可反向还原。 */
    @Column(name = "secret_hash", nullable = false, length = 512)
    private String secretHash;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scopes;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
