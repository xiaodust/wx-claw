package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 控制台账号仓储：用户名全局唯一。 */
public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long> {
    Optional<TenantAccount> findByUsername(String username);

    Optional<TenantAccount> findByContactEmail(String contactEmail);

    /** 每个租户最多一个控制台账号，用于判断是否已完善账号信息。 */
    Optional<TenantAccount> findByTenantId(String tenantId);

    boolean existsByUsername(String username);
}
