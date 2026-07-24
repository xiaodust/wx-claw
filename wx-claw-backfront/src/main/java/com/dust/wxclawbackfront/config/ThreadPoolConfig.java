package com.dust.wxclawbackfront.config;

import com.dust.wxclawbackfront.tenancy.TenantContextTaskDecorator;
import com.dust.wxclawbackfront.tenancy.TenantContextExecutorService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@ConfigurationProperties(prefix = "wxclaw.thread-pool")
@Data
public class ThreadPoolConfig {

    private final TenantContextTaskDecorator tenantContextTaskDecorator;

    @Autowired
    public ThreadPoolConfig(TenantContextTaskDecorator tenantContextTaskDecorator) {
        this.tenantContextTaskDecorator = tenantContextTaskDecorator;
    }

    private PoolConfig messageProcessing = new PoolConfig(4, 8, 100);
    private PoolConfig asyncSave = new PoolConfig(2, 4, 100);
    private PoolConfig promptExecutor = new PoolConfig(2, 4, 50);
    private PoolConfig videoExecutor = new PoolConfig(1, 2, 10);
    private PoolConfig botRuntime = new PoolConfig(1, 32, 0);

    @Data
    public static class PoolConfig {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;

        public PoolConfig(int coreSize, int maxSize, int queueCapacity) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }
    }

    @Bean("messageProcessingExecutor")
    public ExecutorService messageProcessingExecutor() {
        return createExecutor("msg-process-", messageProcessing, true);
    }

    @Bean("asyncSaveExecutor")
    public ExecutorService asyncSaveExecutor() {
        return createExecutor("async-save-", asyncSave, false);
    }

    @Bean("promptExecutor")
    public ExecutorService promptExecutor() {
        return createExecutor("prompt-", promptExecutor, false);
    }

    @Bean("videoExecutor")
    public ExecutorService videoExecutor() {
        return createExecutor("video-", videoExecutor, false);
    }

    @Bean("botRuntimeExecutor")
    public ExecutorService botRuntimeExecutor() {
        return createExecutor("ilink-runtime-", botRuntime, false);
    }

    private ExecutorService createExecutor(String prefix, PoolConfig config, boolean callerRunsPolicy) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(prefix);
        if (callerRunsPolicy) {
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        }
        executor.initialize();
        return new TenantContextExecutorService(executor.getThreadPoolExecutor(), tenantContextTaskDecorator);
    }
}
