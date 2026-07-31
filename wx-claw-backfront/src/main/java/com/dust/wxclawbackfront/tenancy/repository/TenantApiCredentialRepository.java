package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** API 凭据仓储，主要供认证入口按公开 credentialId 定位哈希记录。 */
public interface TenantApiCredentialRepository extends JpaRepository<TenantApiCredential, Long> {
    /** credentialId 全局唯一，认证成功后再从记录中取得其 tenantId。 */
    Optional<TenantApiCredential> findByCredentialId(String credentialId);
}
