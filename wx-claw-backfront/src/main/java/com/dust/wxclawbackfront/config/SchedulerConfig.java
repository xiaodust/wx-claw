package com.dust.wxclawbackfront.config;

import com.dust.wxclawbackfront.tenancy.TenantContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器配置。
 *
 * <p>任务由已有租户调用链动态调度时，装饰器会传播提交时的上下文；固定周期任务若要
 * 访问租户数据，仍应在任务内部逐租户显式建立并清理上下文。</p>
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    private final TenantContextTaskDecorator tenantContextTaskDecorator;

    public SchedulerConfig(TenantContextTaskDecorator tenantContextTaskDecorator) {
        this.tenantContextTaskDecorator = tenantContextTaskDecorator;
    }

    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("task-scheduler-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        // 与普通业务线程池使用同一装饰器，保持异步上下文传播规则一致。
        scheduler.setTaskDecorator(tenantContextTaskDecorator);
        scheduler.initialize();
        return scheduler;
    }
}
