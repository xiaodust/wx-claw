package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantPasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/** 密码重置令牌仓储。 */
public interface TenantPasswordResetRepository extends JpaRepository<TenantPasswordReset, Long> {
    Optional<TenantPasswordReset> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime time);
}
