package com.dust.wxclawbackfront.tenancy;

import jakarta.persistence.PrePersist;

public class TenantOwnedEntityListener {

    @PrePersist
    public void assignTenant(TenantOwnedEntity entity) {
        TenantContext context = TenantContextHolder.require();
        String tenantId = entity.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            entity.setTenantId(context.tenantId());
        } else if (!tenantId.equals(context.tenantId())) {
            throw new SecurityException("Cannot persist an entity for another tenant");
        }
    }
}
