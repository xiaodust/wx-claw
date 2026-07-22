package com.dust.wxclawbackfront.bot.dao.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提醒任务实体
 */
@Data
@Entity
@Table(name = "reminder_task")
public class ReminderTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 企微用户ID
     */
    @Column(nullable = false, length = 128)
    private String userId;

    /**
     * 提醒内容
     */
    @Column(nullable = false, length = 500)
    private String reminderText;

    /**
     * 动作类型：REMINDER(提醒), EMAIL(邮件), WEATHER_PUSH(天气推送), NEWS_PUSH(新闻推送), AI_CHAT(AI对话)
     */
    @Column(length = 50)
    private String actionType;

    /**
     * 动作参数（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String actionParams;

    /**
     * 触发时间
     */
    @Column(nullable = false)
    private LocalDateTime triggerTime;

    /**
     * 任务类型：ONE_TIME(一次性), DAILY(每天), WEEKLY(每周), MONTHLY(每月)
     */
    @Column(nullable = false, length = 20)
    private String taskType;

    /**
     * Cron表达式（周期任务用）
     */
    @Column(length = 100)
    private String cronExpression;

    /**
     * 状态：PENDING(待执行), EXECUTED(已执行), CANCELLED(已取消), FAILED(执行失败)
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 执行时间
     */
    @Column
    private LocalDateTime executedAt;

    /**
     * 失败原因
     */
    @Column(length = 500)
    private String failureReason;

    /**
     * 重试次数
     */
    @Column(nullable = false)
    private Integer retryCount = 0;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
