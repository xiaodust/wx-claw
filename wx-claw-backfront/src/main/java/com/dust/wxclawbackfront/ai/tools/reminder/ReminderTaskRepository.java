package com.dust.wxclawbackfront.ai.tools.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒任务持久化接口
 */
@Repository
public interface ReminderTaskRepository extends JpaRepository<ReminderTask, Long> {

    /**
     * 查询所有指定状态的任务
     */
    List<ReminderTask> findByStatus(String status);

    /**
     * 查询到期待执行的任务
     */
    List<ReminderTask> findByStatusAndTriggerTimeBefore(String status, LocalDateTime time);

    /**
     * 查询用户的待执行任务
     */
    List<ReminderTask> findByUserIdAndStatusOrderByTriggerTimeAsc(String userId, String status);

    /**
     * 查询用户的所有任务（按创建时间倒序）
     */
    List<ReminderTask> findByUserIdOrderByCreatedAtDesc(String userId);
}
