package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.TenantInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** 注册邀请码仓储。 */
public interface TenantInviteCodeRepository extends JpaRepository<TenantInviteCode, Long> {
    Optional<TenantInviteCode> findByCode(String code);

    /**
     * 原子扣减一次使用额度：仅当邀请码存在、启用、未过期且未超配额时成功。
     * 返回受影响行数（1 = 成功），并发下同一时刻只有一个请求能成功。
     */
    @Modifying
    @Query("""
            UPDATE TenantInviteCode c
               SET c.usedCount = c.usedCount + 1
             WHERE c.code = :code
               AND c.status = 'ACTIVE'
               AND (c.expiresAt IS NULL OR c.expiresAt > :now)
               AND (c.quota IS NULL OR c.usedCount < c.quota)
            """)
    int consume(@Param("code") String code, @Param("now") LocalDateTime now);
}
