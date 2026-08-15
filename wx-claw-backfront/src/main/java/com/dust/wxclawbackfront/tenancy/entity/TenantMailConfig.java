package com.dust.wxclawbackfront.tenancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tenant_mail_config")
public class TenantMailConfig {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "password_cipher", nullable = false, columnDefinition = "TEXT")
    private String passwordCipher;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
