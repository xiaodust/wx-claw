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
 * 外部渠道用户与租户内部用户的映射。
 *
 * <p>同一个微信用户在不同 Bot 下可拥有不同渠道身份，但在租户内统一映射到
 * internalUserId，供会话、记忆和权限数据关联。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "tenant_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "channel", "bot_id", "channel_user_id"}),
        @UniqueConstraint(columnNames = {"tenant_id", "internal_user_id"})
})
public class TenantUser extends TenantOwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_user_id", nullable = false, length = 128)
    private String internalUserId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "bot_id", nullable = false, length = 128)
    private String botId;

    @Column(name = "channel_user_id", nullable = false, length = 128)
    private String channelUserId;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 逗号或空白分隔的业务角色集合。 */
    @Column(columnDefinition = "TEXT")
    private String roles;
}
