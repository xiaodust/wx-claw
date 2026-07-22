package com.dust.wxclawbackfront.bot.agent.model;

import lombok.Builder;
import lombok.Data;

/**
 * 单个任务步骤的执行结果
 */
@Data
@Builder
public class TaskResult {

    private boolean success;
    private String textResult;
    private byte[] mediaBytes;
    private String mediaType;
    private String mediaFileName;
    private String errorMessage;
    private long executionTimeMs;

    public boolean hasMedia() {
        return mediaBytes != null && mediaBytes.length > 0;
    }

    public static TaskResult success(String textResult, long executionTimeMs) {
        return TaskResult.builder()
                .success(true)
                .textResult(textResult)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    public static TaskResult successWithMedia(String textResult, byte[] mediaBytes,
                                               String mediaType, String mediaFileName,
                                               long executionTimeMs) {
        return TaskResult.builder()
                .success(true)
                .textResult(textResult)
                .mediaBytes(mediaBytes)
                .mediaType(mediaType)
                .mediaFileName(mediaFileName)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    public static TaskResult failure(String errorMessage, long executionTimeMs) {
        return TaskResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .build();
    }
}
