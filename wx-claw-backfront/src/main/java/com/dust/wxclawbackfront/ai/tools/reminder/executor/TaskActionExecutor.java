package com.dust.wxclawbackfront.ai.tools.reminder.executor;

import com.dust.wxclawbackfront.ai.dao.entity.ReminderTask;

/**
 * 定时任务动作执行器接口
 */
public interface TaskActionExecutor {
    
    /**
     * 执行任务
     * @param task 任务实体
     * @return 执行成功返回 true
     */
    boolean execute(ReminderTask task);
    
    /**
     * 获取执行器支持的动作类型
     */
    String getActionType();
}
