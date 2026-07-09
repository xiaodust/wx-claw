package com.dust.wxclawbackfront.ai.tools.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 提醒任务业务处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderHandler {

    private final ReminderTaskRepository repository;

    @Value("${wxclaw.ai.time.zone:Asia/Shanghai}")
    private String timeZone;

    @Value("${wxclaw.reminder.max-delay-days:30}")
    private int maxDelayDays;

    /**
     * 创建一次性延迟提醒
     */
    @Transactional
    public ReminderCreateResult createDelayReminder(String userId, String reminderText, int delayMinutes) {
        if (userId == null || userId.isBlank()) {
            return new ReminderCreateResult(false, null, "用户ID为空");
        }
        if (reminderText == null || reminderText.isBlank()) {
            return new ReminderCreateResult(false, null, "提醒内容为空");
        }
        if (delayMinutes <= 0) {
            return new ReminderCreateResult(false, null, "延迟时间必须大于0分钟");
        }
        if (delayMinutes > maxDelayDays * 24 * 60) {
            return new ReminderCreateResult(false, null, "延迟时间不能超过" + maxDelayDays + "天");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        LocalDateTime triggerTime = now.plusMinutes(delayMinutes);

        ReminderTask task = new ReminderTask();
        task.setUserId(userId);
        task.setReminderText(reminderText);
        task.setTriggerTime(triggerTime);
        task.setTaskType("ONE_TIME");
        task.setStatus("PENDING");

        ReminderTask saved = repository.save(task);
        log.info("创建延迟提醒成功: userId={}, reminderId={}, triggerTime={}, text={}", 
                userId, saved.getId(), triggerTime, reminderText);

        return new ReminderCreateResult(true, saved.getId(), 
                String.format("好的，我会在 %d 分钟后（%s）提醒你：%s", 
                        delayMinutes, formatTime(triggerTime), reminderText));
    }

    /**
     * 查询用户的待执行提醒列表
     */
    public List<ReminderTask> listPendingReminders(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return repository.findByUserIdAndStatusOrderByTriggerTimeAsc(userId, "PENDING");
    }

    /**
     * 取消提醒
     */
    @Transactional
    public ReminderCancelResult cancelReminder(String userId, Long reminderId) {
        if (userId == null || userId.isBlank()) {
            return new ReminderCancelResult(false, "用户ID为空");
        }
        if (reminderId == null) {
            return new ReminderCancelResult(false, "提醒ID为空");
        }

        return repository.findById(reminderId)
                .map(task -> {
                    if (!task.getUserId().equals(userId)) {
                        return new ReminderCancelResult(false, "无权取消此提醒");
                    }
                    if (!"PENDING".equals(task.getStatus())) {
                        return new ReminderCancelResult(false, "提醒已执行或已取消");
                    }
                    task.setStatus("CANCELLED");
                    task.setExecutedAt(LocalDateTime.now(ZoneId.of(timeZone)));
                    repository.save(task);
                    log.info("取消提醒成功: userId={}, reminderId={}", userId, reminderId);
                    return new ReminderCancelResult(true, "已取消提醒：" + task.getReminderText());
                })
                .orElse(new ReminderCancelResult(false, "提醒不存在"));
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(LocalDateTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        return String.format("%d月%d日 %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), hour, minute);
    }

    /**
     * 创建提醒结果
     */
    public record ReminderCreateResult(boolean success, Long reminderId, String message) {}

    /**
     * 取消提醒结果
     */
    public record ReminderCancelResult(boolean success, String message) {}
}
