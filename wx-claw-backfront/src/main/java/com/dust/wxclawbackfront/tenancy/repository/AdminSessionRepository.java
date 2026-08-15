package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/** 管理端会话仓储。 */
public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {
    Optional<AdminSession> findByTokenHash(String tokenHash);

    long deleteByAdminAccountId(Long adminAccountId);

    long deleteByExpiresAtBefore(LocalDateTime time);
}
