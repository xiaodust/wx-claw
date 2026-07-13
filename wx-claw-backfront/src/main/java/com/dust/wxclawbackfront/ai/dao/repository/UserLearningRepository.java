package com.dust.wxclawbackfront.ai.dao.repository;

import com.dust.wxclawbackfront.ai.dao.entity.UserLearning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserLearningRepository extends JpaRepository<UserLearning, Long> {

    List<UserLearning> findByUserIdAndActiveTrue(String userId);

    List<UserLearning> findByUserIdAndTriggerAndActiveTrue(String userId, String trigger);

    void deleteByUserId(String userId);
}
