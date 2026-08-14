package com.dust.wxclawbackfront.tenancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户级 LLM 配置。
 *
 * <p>用户可在使用页面配置自己的 API Key，覆盖后端默认 key。key 以明文保存
 * （调用方 LLM 服务需要原文），读取/回显一律脱敏，不写入日志。</p>
 */
@Data
@Entity
@Table(name = "tenant_ai_config")
public class TenantAiConfig {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
