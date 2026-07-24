package com.dust.wxclawbackfront.config;

import com.dust.wxclawbackfront.tenancy.TenantContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
        scheduler.setTaskDecorator(tenantContextTaskDecorator);
        scheduler.initialize();
        return scheduler;
    }
}
