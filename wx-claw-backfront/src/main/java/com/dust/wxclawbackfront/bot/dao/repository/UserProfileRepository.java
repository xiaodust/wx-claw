package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    List<UserProfile> findByUserId(String userId);

    List<UserProfile> findByUserIdAndCategory(String userId, String category);

    Optional<UserProfile> findByUserIdAndCategoryAndKeyName(String userId, String category, String keyName);

    void deleteByUserId(String userId);
}
