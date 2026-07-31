package com.dust.wxclawbackfront.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 所有租户私有实体的公共基类。
 *
 * <p>继承后自动获得不可更新的 {@code tenant_id} 字段，并通过
 * {@link TenantOwnedEntityListener} 在首次持久化时校验归属。租户主表本身不继承该类。</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(TenantOwnedEntityListener.class)
public abstract class TenantOwnedEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;
}
