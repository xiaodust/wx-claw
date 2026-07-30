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

    @Column(name = "credential_ref", length = 255)
    private String credentialRef;

    @Column(name = "resume_context_path", length = 512)
    private String resumeContextPath;
}
