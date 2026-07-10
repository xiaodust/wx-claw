package com.dust.wxclawbackfront.scheduler;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTaskRepository;
import com.dust.wxclawbackfront.ai.tools.reminder.executor.TaskActionExecutor;
import com.dust.wxclawbackfront.ai.tools.reminder.executor.TaskActionExecutorRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态任务调度服务
 * 负责管理所有定时任务的内存调度，替代扫库轮询
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicTaskSchedulerService {

    private final TaskScheduler taskScheduler;
    private final ReminderTaskRepository repository;
    private final TaskActionExecutorRegistry executorRegistry;

    @Value("${wxclaw.ai.time.zone:Asia/Shanghai}")
    private String timeZone;

    // 任务ID -> 调度句柄
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 注册一次性延迟任务
     */
    public void scheduleOnceTask(ReminderTask task) {
        if (task.getTriggerTime() == null) {
            log.error("任务触发时间为空，无法注册: taskId={}", task.getId());
            return;
        }

        Instant triggerInstant = task.getTriggerTime().atZone(ZoneId.of(timeZone)).toInstant();
        
        // 如果触发时间已过，立即执行
        if (triggerInstant.isBefore(Instant.now())) {
            log.warn("任务触发时间已过，立即执行: taskId={}, triggerTime={}", task.getId(), task.getTriggerTime());
            executeTask(task);
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> executeTask(task),
            triggerInstant
        );

        scheduledTasks.put(task.getId(), future);
        log.info("已注册一次性任务: taskId={}, triggerTime={}, actionType={}", 
                task.getId(), task.getTriggerTime(), task.getActionType());
    }

    /**
     * 注册周期性任务（使用 cron 表达式）
     */
    public void scheduleCronTask(ReminderTask task) {
        String cronExpression = task.getCronExpression();
        if (cronExpression == null || cronExpression.isBlank()) {
            log.error("Cron表达式为空，无法注册周期任务: taskId={}", task.getId());
            return;
        }

        try {
            CronTrigger trigger = new CronTrigger(cronExpression, ZoneId.of(timeZone));
            
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task),
                trigger
            );

            scheduledTasks.put(task.getId(), future);
            log.info("已注册周期任务: taskId={}, cron={}, actionType={}", 
                    task.getId(), cronExpression, task.getActionType());
        } catch (Exception e) {
            log.error("Cron表达式解析失败: taskId={}, cron={}, error={}", 
                    task.getId(), cronExpression, e.getMessage(), e);
        }
    }

    /**
     * 取消任务调度
     */
    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            log.info("已取消任务调度: taskId={}", taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        int scheduledCount = scheduledTasks.size();
        log.info("调度服务开始关闭，待清理任务数: {}", scheduledCount);
        scheduledTasks.forEach((taskId, future) -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        });
        scheduledTasks.clear();
        log.info("调度服务关闭完成");
    }

    /**
     * 执行任务
     */
    @Transactional
    protected void executeTask(ReminderTask task) {
        Long taskId = task.getId();
        try {
            log.info("开始执行任务: taskId={}, userId={}, actionType={}, text={}", 
                    taskId, task.getUserId(), task.getActionType(), task.getReminderText());

            // 重新从数据库加载任务，确保状态最新
            task = repository.findById(taskId).orElse(null);
            if (task == null) {
                log.warn("任务已被删除: taskId={}", taskId);
                scheduledTasks.remove(taskId);
                return;
            }

            if (!"PENDING".equals(task.getStatus())) {
                log.warn("任务状态不是PENDING，跳过执行: taskId={}, status={}", taskId, task.getStatus());
                return;
            }

            // 获取执行器
            String actionType = task.getActionType();
            if (actionType == null || actionType.isBlank()) {
                actionType = "REMINDER";
            }

            TaskActionExecutor executor = executorRegistry.getExecutor(actionType);
            if (executor == null) {
                log.error("未找到执行器: taskId={}, actionType={}", taskId, actionType);
                handleFailure(task, "未找到执行器：" + actionType);
                return;
            }

            // 执行任务
            boolean success = executor.execute(task);
            LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));

            if (success) {
                String taskType = task.getTaskType();
                
                if ("ONE_TIME".equals(taskType)) {
                    // 一次性任务，标记为已执行
                    task.setStatus("EXECUTED");
                    task.setExecutedAt(now);
                    repository.save(task);
                    scheduledTasks.remove(taskId);
                    log.info("一次性任务执行成功: taskId={}", taskId);
                } else {
                    // 周期任务，更新执行时间（但保持 PENDING 状态，继续等待下次触发）
                    task.setExecutedAt(now);
                    repository.save(task);
                    log.info("周期任务执行成功: taskId={}, nextTrigger will be determined by cron", taskId);
                }
            } else {
                handleFailure(task, "执行失败");
            }

        } catch (Exception e) {
            log.error("任务执行异常: taskId={}, error={}", taskId, e.getMessage(), e);
            handleFailure(task, e.getMessage());
        }
    }

    /**
     * 处理任务执行失败
     */
    private void handleFailure(ReminderTask task, String errorMessage) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setFailureReason(errorMessage);

        // 一次性任务失败后标记为 FAILED
        if ("ONE_TIME".equals(task.getTaskType())) {
            task.setStatus("FAILED");
            task.setExecutedAt(LocalDateTime.now(ZoneId.of(timeZone)));
            scheduledTasks.remove(task.getId());
            log.warn("一次性任务执行失败: taskId={}, error={}", task.getId(), errorMessage);
        } else {
            // 周期任务失败，记录错误但保持 PENDING，等待下次重试
            log.warn("周期任务执行失败，将在下次触发时重试: taskId={}, error={}", task.getId(), errorMessage);
        }

        repository.save(task);
    }

    /**
     * 获取当前调度中的任务数量
     */
    public int getScheduledTaskCount() {
        return scheduledTasks.size();
    }
}
