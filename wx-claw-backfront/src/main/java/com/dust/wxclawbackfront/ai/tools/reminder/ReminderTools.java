package com.dust.wxclawbackfront.ai.tools.reminder;

import com.dust.wxclawbackfront.ai.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 提醒功能 AI 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 40;
    }

    private final ReminderHandler reminderHandler;
    private final AiToolInvocationStore invocationStore;

    /**
     * 创建一次性延迟提醒
     */
    @Tool(name = "reminder_create", description = "为用户创建一次性延迟提醒。当用户说\"X分钟后提醒我做某事\"、\"过一会提醒我...\"等时调用。参数 reminderText 是提醒内容，delayMinutes 是延迟的分钟数（必须大于0）。")
    public ReminderCreateToolResult createReminder(String reminderText, int delayMinutes) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 reminder_create: userId={}, reminderText={}, delayMinutes={}", userId, reminderText, delayMinutes);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID，提醒创建失败";
            invocationStore.add("reminder_create", 
                    String.format("reminderText=%s, delayMinutes=%d", reminderText, delayMinutes),
                    errorMsg);
            return new ReminderCreateToolResult(false, null, errorMsg);
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDelayReminder(userId, reminderText, delayMinutes);
        
        invocationStore.add("reminder_create",
                String.format("reminderText=%s, delayMinutes=%d", reminderText, delayMinutes),
                result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建延迟网络搜索推送
     */
    @Tool(name = "create_delay_web_search",
          description = "创建一次性延迟网络搜索推送。在指定分钟数后自动搜索并推送结果。适用于'过X分钟帮我搜索'、'X分钟后推送XX资讯'等场景。")
    public ReminderCreateToolResult createDelayWebSearch(String query, String freshness, int count, int delayMinutes) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDelayWebSearch(userId, query, freshness, count, delayMinutes);
        
        String args = String.format("query=%s, delayMinutes=%d", query, delayMinutes);
        invocationStore.add("create_delay_web_search", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建延迟 AI 聊天
     */
    @Tool(name = "create_delay_ai_chat",
          description = "创建一次性延迟 AI 自动聊天。在指定分钟数后让 AI 根据提示词自动生成内容并发送。适用于'过X分钟让AI给我...'、'X分钟后AI自动...'等场景。")
    public ReminderCreateToolResult createDelayAiChat(String prompt, int delayMinutes) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDelayAiChat(userId, prompt, delayMinutes);
        
        String args = String.format("prompt=%s, delayMinutes=%d", prompt, delayMinutes);
        invocationStore.add("create_delay_ai_chat", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每天定时提醒
     */
    @Tool(name = "create_daily_reminder",
          description = "创建每天定时提醒。适用于需要每天固定时间提醒的场景。")
    public ReminderCreateToolResult createDailyReminder(String reminderText, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailyReminder(userId, reminderText, hour, minute);
        
        String args = String.format("reminderText=%s, hour=%d, minute=%d", reminderText, hour, minute);
        invocationStore.add("create_daily_reminder", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }



    /**
     * 创建每周定时提醒
     */
    @Tool(name = "create_weekly_reminder",
          description = "创建每周定时提醒。适用于需要每周固定时间提醒的场景。dayOfWeek: 1-7（1=周一，7=周日）")
    public ReminderCreateToolResult createWeeklyReminder(String reminderText, int dayOfWeek, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createWeeklyReminder(userId, reminderText, dayOfWeek, hour, minute);
        
        String args = String.format("reminderText=%s, dayOfWeek=%d, hour=%d, minute=%d", reminderText, dayOfWeek, hour, minute);
        invocationStore.add("create_weekly_reminder", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每月定时提醒
     */
    @Tool(name = "create_monthly_reminder",
          description = "创建每月定时提醒。适用于需要每月固定日期提醒的场景。dayOfMonth: 1-31")
    public ReminderCreateToolResult createMonthlyReminder(String reminderText, int dayOfMonth, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createMonthlyReminder(userId, reminderText, dayOfMonth, hour, minute);
        
        String args = String.format("reminderText=%s, dayOfMonth=%d, hour=%d, minute=%d", reminderText, dayOfMonth, hour, minute);
        invocationStore.add("create_monthly_reminder", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每天定时天气推送
     */
    @Tool(name = "schedule_daily_weather",
          description = "创建每天定时天气推送。每天固定时间自动推送指定地点的天气信息。")
    public ReminderCreateToolResult scheduleDailyWeather(String location, int hour, int minute, boolean includeForecast) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailyWeatherPush(userId, location, hour, minute, includeForecast);
        
        String args = String.format("location=%s, hour=%d, minute=%d, includeForecast=%b", location, hour, minute, includeForecast);
        invocationStore.add("schedule_daily_weather", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每天定时邮件发送
     */
    @Tool(name = "schedule_daily_email",
          description = "创建每天定时邮件发送。每天固定时间自动发送邮件到指定邮箱。")
    public ReminderCreateToolResult scheduleDailyEmail(String to, String subject, String content, int hour, int minute, boolean isHtml) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailyEmail(userId, to, subject, content, hour, minute, isHtml);
        
        String args = String.format("to=%s, subject=%s, hour=%d, minute=%d", to, subject, hour, minute);
        invocationStore.add("schedule_daily_email", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每天定时网络搜索推送
     */
    @Tool(name = "schedule_daily_web_search",
          description = "创建每天定时网络搜索推送。每天固定时间自动搜索指定关键词并推送结果。")
    public ReminderCreateToolResult scheduleDailyWebSearch(String query, String freshness, int count, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailyWebSearch(userId, query, freshness, count, hour, minute);
        
        String args = String.format("query=%s, hour=%d, minute=%d", query, hour, minute);
        invocationStore.add("schedule_daily_web_search", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 创建每天定时 AI 聊天
     */
    @Tool(name = "schedule_daily_ai_chat",
          description = "创建每天定时 AI 自动聊天。每天固定时间让 AI 根据提示词自动生成内容并发送。")
    public ReminderCreateToolResult scheduleDailyAiChat(String prompt, int hour, int minute) {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new ReminderCreateToolResult(false, null, "无法获取用户信息");
        }

        ReminderHandler.ReminderCreateResult result = reminderHandler.createDailyAiChat(userId, prompt, hour, minute);
        
        String args = String.format("prompt=%s, hour=%d, minute=%d", prompt, hour, minute);
        invocationStore.add("schedule_daily_ai_chat", args, result.message());

        return new ReminderCreateToolResult(result.success(), result.reminderId(), result.message());
    }

    /**
     * 查询用户的待执行提醒列表
     */
    @Tool(name = "reminder_list", description = "查询当前用户的待执行提醒列表。当用户问\"我有哪些提醒\"、\"查看我的提醒\"等时调用。")
    public ReminderListToolResult listReminders() {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 reminder_list: userId={}", userId);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("reminder_list", "无参数", errorMsg);
            return new ReminderListToolResult(false, List.of(), errorMsg);
        }

        List<ReminderTask> tasks = reminderHandler.listPendingReminders(userId);
        
        List<ReminderInfo> reminders = tasks.stream()
                .map(task -> new ReminderInfo(
                        task.getId(),
                        task.getReminderText(),
                        task.getTriggerTime().toString(),
                        task.getTaskType()))
                .collect(Collectors.toList());

        String message = reminders.isEmpty() ? "你目前没有待执行的提醒" : "找到 " + reminders.size() + " 个待执行提醒";
        
        invocationStore.add("reminder_list", "无参数", message);

        return new ReminderListToolResult(true, reminders, message);
    }

    /**
     * 取消指定的提醒
     */
    @Tool(name = "reminder_cancel", description = "取消用户的某个提醒。当用户说\"取消第X个提醒\"、\"删除提醒ID为XX的提醒\"等时调用。参数 reminderId 是提醒的ID。")
    public ReminderCancelToolResult cancelReminder(long reminderId) {
        String userId = UserContextHolder.getUserId();
        
        log.info("AI调用 reminder_cancel: userId={}, reminderId={}", userId, reminderId);

        if (userId == null || userId.isBlank()) {
            String errorMsg = "无法获取用户ID";
            invocationStore.add("reminder_cancel", "reminderId=" + reminderId, errorMsg);
            return new ReminderCancelToolResult(false, errorMsg);
        }

        ReminderHandler.ReminderCancelResult result = reminderHandler.cancelReminder(userId, reminderId);
        
        invocationStore.add("reminder_cancel",
                "reminderId=" + reminderId,
                result.message());

        return new ReminderCancelToolResult(result.success(), result.message());
    }

    /**
     * 提醒创建结果
     */
    public record ReminderCreateToolResult(boolean success, Long reminderId, String message) {}

    /**
     * 提醒列表结果
     */
    public record ReminderListToolResult(boolean success, List<ReminderInfo> reminders, String message) {}

    /**
     * 提醒信息
     */
    public record ReminderInfo(Long id, String reminderText, String triggerTime, String taskType) {}

    /**
     * 取消提醒结果
     */
    public record ReminderCancelToolResult(boolean success, String message) {}
}
