package com.dust.wxclawbackfront.bot.scheduler;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.dao.repository.ReminderTaskRepository;
import com.dust.wxclawbackfront.bot.agent.tools.reminder.executor.TaskActionExecutor;
import com.dust.wxclawbackfront.bot.agent.tools.reminder.executor.TaskActionExecutorRegistry;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    @Value("${wxclaw.reminder.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${wxclaw.reminder.cleanup.cron:0 0 2 * * ?}")
    private String cleanupCron;

    @Value("${wxclaw.reminder.cleanup.retention-days:7}")
    private int cleanupRetentionDays;

    // 任务ID -> 调度句柄
    private final Map<TenantTaskKey, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 注册一次性延迟任务
     */
    public void scheduleOnceTask(ReminderTask task) {
        if (task.getTriggerTime() == null) {
            log.error("任务触发时间为空，无法注册: taskId={}", task.getId());
            return;
        }

        Instant triggerInstant = task.getTriggerTime().atZone(ZoneId.of(timeZone)).toInstant();

        // 触发时间已过：此时 iLink 可能尚未登录完成，不在此处立即执行以免发送失败。
        // 该任务会在 iLink 登录成功后由 runOverdueOnceTasks() 统一补偿执行。
        if (triggerInstant.isBefore(Instant.now())) {
            log.warn("任务触发时间已过，等待连接就绪后补偿执行: taskId={}, triggerTime={}", task.getId(), task.getTriggerTime());
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> executeTask(task),
            triggerInstant
        );

        scheduledTasks.put(key(task), future);
        log.info("已注册一次性任务: taskId={}, triggerTime={}, actionType={}", 
                task.getId(), task.getTriggerTime(), task.getActionType());
    }

    /**
     * 注册周期性任务（使用 cron 表达式）
     */
    public void scheduleCronTask(ReminderTask task) {
        String cronExpression = sanitizeCron(task.getCronExpression());
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

            scheduledTasks.put(key(task), future);
            log.info("已注册周期任务: taskId={}, cron={}, actionType={}", 
                    task.getId(), cronExpression, task.getActionType());
        } catch (Exception e) {
            log.error("Cron表达式解析失败: taskId={}, cron={}, error={}", 
                    task.getId(), cronExpression, e.getMessage(), e);
        }
    }

    /**
     * 清洗 Cron 表达式：将 Quartz 风格的 ? 替换为 *，兼容数据库中已有的旧数据
     */
    private String sanitizeCron(String cron) {
        if (cron == null) return null;
        return cron.replace('?', '*').trim();
    }

    /**
     * 取消任务调度
     */
    public void cancelTask(Long taskId) {
        String tenantId = TenantContextHolder.require().tenantId();
        ScheduledFuture<?> future = scheduledTasks.remove(new TenantTaskKey(tenantId, taskId));
        if (future != null) {
            future.cancel(false);
            log.info("已取消任务调度: taskId={}", taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        int scheduledCount = scheduledTasks.size();
        log.info("调度服务开始关闭，待清理任务数: {}", scheduledCount);
        scheduledTasks.forEach((taskKey, future) -> {
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
        TenantContext taskContext = new TenantContext(task.getTenantId(), task.getChannel(), task.getBotId(),
                task.getInternalUserId(), task.getChannelUserId(), Set.of(), Set.of(), UUID.randomUUID().toString());
        TenantContextHolder.set(taskContext);
        try {
            log.info("开始执行任务: taskId={}, userId={}, actionType={}, text={}", 
                    taskId, task.getUserId(), task.getActionType(), task.getReminderText());

            // 重新从数据库加载任务，确保状态最新
            task = repository.findByTenantIdAndId(taskContext.tenantId(), taskId).orElse(null);
            if (task == null) {
                log.warn("任务已被删除: taskId={}", taskId);
                scheduledTasks.remove(new TenantTaskKey(taskContext.tenantId(), taskId));
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
                    scheduledTasks.remove(new TenantTaskKey(taskContext.tenantId(), taskId));
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
        } finally {
            TenantContextHolder.clear();
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
            scheduledTasks.remove(key(task));
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

    /**
     * 定时清理已取消、已失败和已执行的任务
     * 每天凌晨2点执行，清理7天前已取消、已失败和已执行的任务
     */
    @Scheduled(cron = "${wxclaw.reminder.cleanup.cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupCancelledTasks() {
        if (!cleanupEnabled) {
            return;
        }
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now(ZoneId.of(timeZone)).minusDays(cleanupRetentionDays);
            
            // 清理已取消的任务
            long cancelledCount = repository.deleteByStatusAndCreatedAtBefore("CANCELLED", cutoffTime);
            if (cancelledCount > 0) {
                log.info("已清理 {} 个已取消的任务（创建时间早于 {}）", cancelledCount, cutoffTime);
            }
            
            // 清理已失败的任务
            long failedCount = repository.deleteByStatusAndCreatedAtBefore("FAILED", cutoffTime);
            if (failedCount > 0) {
                log.info("已清理 {} 个已失败的任务（创建时间早于 {}）", failedCount, cutoffTime);
            }
            
            // 清理已执行的任务
            long executedCount = repository.deleteByStatusAndCreatedAtBefore("EXECUTED", cutoffTime);
            if (executedCount > 0) {
                log.info("已清理 {} 个已执行的任务（创建时间早于 {}）", executedCount, cutoffTime);
            }
        } catch (Exception e) {
            log.error("清理已取消任务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 补偿执行触发时间已过的一次性任务。
     * 应在 iLink 登录成功、消息发送通道就绪后调用，避免连接未就绪时发送失败。
     */
    public void runOverdueOnceTasks(BotRuntimeKey runtimeKey) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        List<ReminderTask> overdueTasks = repository.findByTenantIdAndBotIdAndStatusAndTriggerTimeBefore(
                runtimeKey.tenantId(), runtimeKey.botId(), "PENDING", now);

        int count = 0;
        for (ReminderTask task : overdueTasks) {
            if (!"ONE_TIME".equals(task.getTaskType())) {
                continue;
            }

            ScheduledFuture<?> future = scheduledTasks.remove(key(task));
            if (future != null) {
                future.cancel(false);
            }

            log.info("补偿执行过期一次性任务: taskId={}, triggerTime={}", task.getId(), task.getTriggerTime());
            executeTask(task);
            count++;
        }

        if (count > 0) {
            log.info("过期一次性任务补偿执行完成，共处理 {} 个", count);
        } else {
            log.info("没有需要补偿执行的过期一次性任务");
        }
    }

    private TenantTaskKey key(ReminderTask task) {
        return new TenantTaskKey(task.getTenantId(), task.getId());
    }

    private record TenantTaskKey(String tenantId, Long taskId) {
    }
}
