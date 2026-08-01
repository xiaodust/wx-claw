package com.dust.wxclawbackfront.bot.agent.career.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "wxclaw.career")
public class JobHelperProperties {
    private boolean enabled;
    private String apiKey;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration resumeContextTtl = Duration.ofDays(365);
    private DataSize maxResumeSize = DataSize.ofMegabytes(10);
    private int maxResultsPerMessage = 10;
    private final Task task = new Task();

    @Data
    public static class Task {
        private int coreSize = 2;
        private int maxSize = 4;
        private int queueCapacity = 20;
    }
}
