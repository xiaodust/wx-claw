package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderNotifier;
import com.dust.wxclawbackfront.ai.dao.entity.ReminderTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提醒动作执行器
 * 发送微信提醒消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderActionExecutor implements TaskActionExecutor {
    
    private final ReminderNotifier reminderNotifier;
    
    @Override
    public boolean execute(ReminderTask task) {
        log.info("执行提醒任务: taskId={}, userId={}, text={}", 
                task.getId(), task.getUserId(), task.getReminderText());
        
        return reminderNotifier.sendReminder(task);
    }
    
    @Override
    public String getActionType() {
        return "REMINDER";
    }
}
