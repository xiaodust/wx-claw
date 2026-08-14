package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户主数据仓储。该表不属于某个租户，因此查询时不依赖 {@code TenantContext}。
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    /** 使用业务隔离键定位租户，而不是使用数据库自增主键。 */
    Optional<Tenant> findByTenantId(String tenantId);

    /** 租户编码全局唯一，用于注册时校验编码是否已被占用。 */
    Optional<Tenant> findByTenantCode(String tenantCode);
}
