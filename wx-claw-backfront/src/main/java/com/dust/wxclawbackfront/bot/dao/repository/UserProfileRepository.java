package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    List<UserProfile> findByTenantIdAndUserId(String tenantId, String userId);

    List<UserProfile> findByTenantIdAndUserIdAndCategory(String tenantId, String userId, String category);

    Optional<UserProfile> findByTenantIdAndUserIdAndCategoryAndKeyName(String tenantId, String userId, String category, String keyName);

    void deleteByTenantIdAndUserId(String tenantId, String userId);
}
