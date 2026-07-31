package com.dust.wxclawbackfront.tenancy;

import jakarta.persistence.PrePersist;

/**
 * JPA 新增实体时的租户归属守卫。
 *
 * <p>未显式设置 tenantId 时使用当前上下文自动填充；显式设置时必须与当前租户一致，
 * 从实体写入入口阻止伪造其他租户的数据。</p>
 */
public class TenantOwnedEntityListener {

    @PrePersist
    public void assignTenant(TenantOwnedEntity entity) {
        // 不允许在缺少上下文时静默写入 default，避免后台任务产生错误归属的数据。
        TenantContext context = TenantContextHolder.require();
        String tenantId = entity.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            entity.setTenantId(context.tenantId());
        } else if (!tenantId.equals(context.tenantId())) {
            throw new SecurityException("Cannot persist an entity for another tenant");
        }
    }
}
