package com.dust.wxclawbackfront.ai.tools.reminder;

import com.dust.wxclawbackfront.ilnk.outbound.ILinkMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提醒消息发送器
 * 负责将提醒内容发送给用户，与定时触发逻辑分离
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderNotifier {

    private final ILinkMessageSender messageSender;

    /**
     * 发送提醒消息
     * @param task 提醒任务
     * @return 发送成功返回 true
     */
    public boolean sendReminder(ReminderTask task) {
        if (task == null || task.getUserId() == null || task.getReminderText() == null) {
            log.warn("提醒任务参数不完整，无法发送: taskId={}", task == null ? null : task.getId());
            return false;
        }

        String reminderMessage = formatReminderMessage(task.getReminderText());

        try {
            messageSender.sendText(task.getUserId(), reminderMessage);
            log.info("提醒消息发送成功: userId={}, taskId={}, message={}", task.getUserId(), task.getId(), reminderMessage);
            return true;
        } catch (Exception e) {
            log.error("提醒消息发送失败: userId={}, taskId={}, error={}", task.getUserId(), task.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 格式化提醒消息
     */
    private String formatReminderMessage(String reminderText) {
        return "⏰ 提醒：" + reminderText;
    }
}
