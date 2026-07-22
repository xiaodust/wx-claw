package com.dust.wxclawbackfront.bot.scheduler;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.dao.repository.ReminderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动监听器
 * 应用启动后从数据库加载所有 PENDING 状态的任务并注册到调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSchedulerInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ReminderTaskRepository repository;
    private final DynamicTaskSchedulerService schedulerService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("应用启动完成，开始加载待执行任务...");

        try {
            // 查询所有 PENDING 状态的任务，统一恢复到内存调度器
            List<ReminderTask> pendingTasks = repository.findByStatus("PENDING");

            if (pendingTasks.isEmpty()) {
                log.info("没有待执行任务");
                return;
            }

            log.info("发现 {} 个待执行任务，开始注册到调度器", pendingTasks.size());

            int oneTimeCount = 0;
            int cronCount = 0;

            for (ReminderTask task : pendingTasks) {
                try {
                    String taskType = task.getTaskType();

                    if ("ONE_TIME".equals(taskType)) {
                        schedulerService.scheduleOnceTask(task);
                        oneTimeCount++;
                    } else {
                        schedulerService.scheduleCronTask(task);
                        cronCount++;
                    }
                } catch (Exception e) {
                    log.error("任务注册失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
                }
            }

            log.info("任务加载完成：一次性任务 {} 个，周期任务 {} 个", oneTimeCount, cronCount);

        } catch (Exception e) {
            log.error("任务加载过程异常", e);
        }
    }
}
