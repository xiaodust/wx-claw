package com.dust.wxclawbackfront.bot.dao.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户画像实体
 * 存储用户的基本信息、偏好、习惯等
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "user_profile", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "user_id", "category", "key_name"}))
public class UserProfile extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    private Long id;

    @Column(nullable = false, length = 128)
    private String userId;

    /**
     * 分类：basic_info / preference / habit / decision
     */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * 键名，如 city / reply_style / sleep_time
     */
    @Column(nullable = false, length = 100)
    private String keyName;

    /**
     * 值
     */
    @Column(nullable = false, length = 500)
    private String keyValue;

    /**
     * 来源：user_told / ai_detected
     */
    @Column(length = 20)
    private String source;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (source == null) source = "ai_detected";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
