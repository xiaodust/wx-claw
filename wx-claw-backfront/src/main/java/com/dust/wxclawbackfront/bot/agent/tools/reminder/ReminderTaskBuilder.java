package com.dust.wxclawbackfront.bot.agent.tools.reminder;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ReminderTask 构建器
 * 封装 ReminderTask 的构建逻辑，避免重复代码
 */
public class ReminderTaskBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String userId;
    private String reminderText;
    private LocalDateTime triggerTime;
    private String taskType;
    private String actionType;
    private String actionParams;
    private String cronExpression;

    private ReminderTaskBuilder() {
    }

    /**
     * 创建构建器实例
     */
    public static ReminderTaskBuilder builder() {
        return new ReminderTaskBuilder();
    }

    /**
     * 设置用户ID
     */
    public ReminderTaskBuilder userId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * 设置提醒文本
     */
    public ReminderTaskBuilder reminderText(String reminderText) {
        this.reminderText = reminderText;
        return this;
    }

    /**
     * 设置触发时间
     */
    public ReminderTaskBuilder triggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
        return this;
    }

    /**
     * 设置任务类型（ONE_TIME, DAILY, WEEKLY, MONTHLY）
     */
    public ReminderTaskBuilder taskType(String taskType) {
        this.taskType = taskType;
        return this;
    }

    /**
     * 设置动作类型（REMINDER, WEB_SEARCH_PUSH, AI_CHAT, WEATHER_PUSH, WEATHER_EMAIL, SCHEDULED_BRIEFING_EMAIL, EMAIL, CONVERSATION_SUMMARY）
     */
    public ReminderTaskBuilder actionType(String actionType) {
        this.actionType = actionType;
        return this;
    }

    /**
     * 设置动作参数（Map 形式，会自动序列化为 JSON）
     */
    public ReminderTaskBuilder actionParams(Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            try {
                this.actionParams = objectMapper.writeValueAsString(params);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化参数失败", e);
            }
        }
        return this;
    }

    /**
     * 设置动作参数（JSON 字符串）
     */
    public ReminderTaskBuilder actionParamsJson(String actionParams) {
        this.actionParams = actionParams;
        return this;
    }

    /**
     * 设置 Cron 表达式
     */
    public ReminderTaskBuilder cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    /**
     * 构建 ReminderTask 对象
     */
    public ReminderTask build() {
        ReminderTask task = new ReminderTask();
        task.setUserId(userId);
        task.setReminderText(reminderText);
        task.setTriggerTime(triggerTime);
        task.setTaskType(taskType);
        task.setActionType(actionType);
        task.setActionParams(actionParams);
        task.setCronExpression(cronExpression);
        task.setStatus("PENDING");
        return task;
    }

    /**
     * 构建参数 Map 的便捷方法
     */
    public static Map<String, Object> createParams(String key1, Object value1) {
        Map<String, Object> params = new HashMap<>();
        params.put(key1, value1);
        return params;
    }

    /**
     * 构建参数 Map 的便捷方法
     */
    public static Map<String, Object> createParams(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> params = new HashMap<>();
        params.put(key1, value1);
        params.put(key2, value2);
        return params;
    }

    /**
     * 构建参数 Map 的便捷方法
     */
    public static Map<String, Object> createParams(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
        Map<String, Object> params = new HashMap<>();
        params.put(key1, value1);
        params.put(key2, value2);
        params.put(key3, value3);
        return params;
    }
}
