package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantEmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 邮箱验证码仓储。 */
public interface TenantEmailVerificationRepository extends JpaRepository<TenantEmailVerification, Long> {
    List<TenantEmailVerification> findByEmailAndPurposeOrderByCreatedAtDesc(String email, String purpose);

    @Modifying
    @Query("UPDATE TenantEmailVerification v SET v.usedAt = :now WHERE v.id = :id")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    long deleteByExpiresAtBefore(LocalDateTime time);
}
