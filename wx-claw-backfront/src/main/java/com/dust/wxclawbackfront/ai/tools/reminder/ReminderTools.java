package com.dust.wxclawbackfront.ai.tools.reminder;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
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
public class ReminderTools {

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
