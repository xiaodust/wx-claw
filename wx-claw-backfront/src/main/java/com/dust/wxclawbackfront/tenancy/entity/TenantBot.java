package com.dust.wxclawbackfront.tenancy.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户下的渠道 Bot 配置。
 * channel 与 botId 全局唯一，用于把 iLink 运行实例准确映射回租户。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tenant_bot", uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "bot_id"}))
public class TenantBot extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "bot_id", nullable = false, length = 128)
    private String botId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 外部凭据的引用标识；实体本身不保存渠道明文密钥。 */
    @Column(name = "credential_ref", length = 255)
    private String credentialRef;

    /** 当前 Bot 独立的 iLink 会话恢复文件路径。 */
    @Column(name = "resume_context_path", length = 512)
    private String resumeContextPath;
}
