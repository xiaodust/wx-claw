package com.dust.wxclawbackfront.tenancy.repository;

import com.dust.wxclawbackfront.tenancy.entity.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 平台管理员账号仓储：用户名全局唯一。 */
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
