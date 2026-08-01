package com.dust.wxclawbackfront.bot.agent.career.config;

import com.dust.wxclawbackfront.tenancy.TenantContextExecutorService;
import com.dust.wxclawbackfront.tenancy.TenantContextTaskDecorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;

@Configuration
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class JobHelperConfig {
    @Bean("careerTaskExecutor")
    public ExecutorService careerTaskExecutor(TenantContextTaskDecorator decorator,
                                              JobHelperProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getTask().getCoreSize());
        executor.setMaxPoolSize(properties.getTask().getMaxSize());
        executor.setQueueCapacity(properties.getTask().getQueueCapacity());
        executor.setThreadNamePrefix("career-task-");
        executor.initialize();
        return new TenantContextExecutorService(executor.getThreadPoolExecutor(), decorator);
    }
}
