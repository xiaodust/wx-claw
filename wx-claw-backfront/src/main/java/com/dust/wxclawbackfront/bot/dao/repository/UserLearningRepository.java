package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.UserLearning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLearningRepository extends JpaRepository<UserLearning, Long> {

    List<UserLearning> findByTenantIdAndUserIdAndActiveTrue(String tenantId, String userId);

    List<UserLearning> findByTenantIdAndUserIdAndTriggerAndActiveTrue(String tenantId, String userId, String trigger);

    Optional<UserLearning> findByTenantIdAndId(String tenantId, Long id);

    void deleteByTenantIdAndUserId(String tenantId, String userId);
}
