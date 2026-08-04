package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
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

    List<ReminderTask> findByTenantIdAndBotIdAndStatusAndTriggerTimeBefore(
            String tenantId, String botId, String status, LocalDateTime time);

    java.util.Optional<ReminderTask> findByTenantIdAndId(String tenantId, Long id);

    /**
     * 查询用户的待执行任务
     */
    List<ReminderTask> findByTenantIdAndUserIdAndStatusOrderByTriggerTimeAsc(String tenantId, String userId, String status);

    /**
     * 查询用户的所有任务（按创建时间倒序）
     */
    List<ReminderTask> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId);

    /**
     * 删除指定状态且创建时间早于指定时间的任务
     */
    long deleteByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);

    /**
     * 删除指定任务类型、状态且触发时间早于指定时间的任务（用于清理孤儿 PENDING 一次性任务）
     */
    long deleteByTaskTypeAndStatusAndTriggerTimeBefore(String taskType, String status, LocalDateTime triggerTime);
}
