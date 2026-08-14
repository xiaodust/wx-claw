package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/** 登录会话仓储：按 token 哈希定位。 */
public interface TenantSessionRepository extends JpaRepository<TenantSession, Long> {
    Optional<TenantSession> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime time);
}
