package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 媒体上下文管理器
 * 管理图片、视频、文件的待处理上下文
 */
@Slf4j
@Component
public class MediaContextManager {

    // 待处理的媒体上下文：用户发送媒体后，等待用户说明意图
    private final ConcurrentHashMap<String, String> pendingImageContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingVideoContexts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingFileContexts = new ConcurrentHashMap<>();
    // 待上传的文件：存储文件字节，用于用户确认后上传到知识库
    private final ConcurrentHashMap<String, PendingFileUpload> pendingFileUploads = new ConcurrentHashMap<>();
    // 各待处理上下文的最后更新时间，用于定期清理用户不再续接的媒体上下文
    private final ConcurrentHashMap<String, Instant> pendingImageTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> pendingVideoTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> pendingFileTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> pendingUploadTimes = new ConcurrentHashMap<>();

    @Value("${wxclaw.ilink.media-context-max-idle:PT24H}")
    private Duration maxIdle = Duration.ofHours(24);

    /**
     * 存储图片上下文
     */
    public void storeImageContext(String userId, String imageDescription) {
        pendingImageContexts.put(userId, imageDescription);
        pendingImageTimes.put(userId, Instant.now());
    }

    /**
     * 存储视频上下文
     */
    public void storeVideoContext(String userId, String videoDescription) {
        pendingVideoContexts.put(userId, videoDescription);
        pendingVideoTimes.put(userId, Instant.now());
    }

    /**
     * 存储文件上下文
     */
    public void storeFileContext(String userId, ILinkUserInput userInput) {
        String fileInfo = buildFileInfo(userInput);
        pendingFileContexts.put(userId, fileInfo);
        pendingFileTimes.put(userId, Instant.now());

        storePendingFileUpload(userId, userInput);
    }

    /**
     * 单独保留文件字节，供后续知识库上传
     */
    public void storePendingFileUpload(String userId, ILinkUserInput userInput) {
        if (userInput.getFileBytes() != null && userInput.getFileBytes().length > 0) {
            pendingFileUploads.put(userId, new PendingFileUpload(
                    userInput.getFileName(), userInput.getFileBytes()));
            pendingUploadTimes.put(userId, Instant.now());
        }
    }

    /**
     * 获取并移除图片上下文
     */
    public String takeImageContext(String userId) {
        pendingImageTimes.remove(userId);
        return pendingImageContexts.remove(userId);
    }

    public String getImageContext(String userId) {
        return pendingImageContexts.get(userId);
    }

    public void clearImageContext(String userId) {
        pendingImageTimes.remove(userId);
        pendingImageContexts.remove(userId);
    }

    /**
     * 获取并移除视频上下文
     */
    public String takeVideoContext(String userId) {
        pendingVideoTimes.remove(userId);
        return pendingVideoContexts.remove(userId);
    }

    public String getVideoContext(String userId) {
        return pendingVideoContexts.get(userId);
    }

    public void clearVideoContext(String userId) {
        pendingVideoTimes.remove(userId);
        pendingVideoContexts.remove(userId);
    }

    /**
     * 获取并移除文件上下文
     */
    public String takeFileContext(String userId) {
        pendingFileTimes.remove(userId);
        return pendingFileContexts.remove(userId);
    }

    public String getFileContext(String userId) {
        return pendingFileContexts.get(userId);
    }

    public void clearFileContext(String userId) {
        pendingFileTimes.remove(userId);
        pendingFileContexts.remove(userId);
    }

    /**
     * 获取并移除待上传文件
     */
    public PendingFileUpload takePendingFileUpload(String userId) {
        pendingUploadTimes.remove(userId);
        return pendingFileUploads.remove(userId);
    }

    /**
     * 获取但不移除待上传文件，上传失败时允许用户重试
     */
    public PendingFileUpload getPendingFileUpload(String userId) {
        return pendingFileUploads.get(userId);
    }

    public void clearPendingFileUpload(String userId) {
        pendingUploadTimes.remove(userId);
        pendingFileUploads.remove(userId);
    }

    /**
     * 构建文件信息文本，用于存储到 pending 上下文
     */
    private String buildFileInfo(ILinkUserInput userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户发送了一个文件：").append(userInput.getFileName() != null ? userInput.getFileName() : "未知文件");
        if (userInput.getFileSize() != null) {
            sb.append("（大小：").append(userInput.getFileSize()).append("字节）");
        }
        if (userInput.getFileContent() != null && !userInput.getFileContent().isBlank()) {
            sb.append("。文件内容如下：\n").append(userInput.getFileContent());
        } else {
            sb.append("。未能解析文件内容。");
        }
        return sb.toString();
    }

    /**
     * 清理超过 {@code maxIdle} 未更新的待处理媒体上下文（含待上传文件字节），
     * 防止用户发送媒体后不再续接指令导致的内存驻留。
     */
    public void cleanupExpired(Duration maxIdle) {
        cleanupExpired(maxIdle, Instant.now());
    }

    /**
     * 供测试指定当前时间；正常调度走 {@link #cleanupExpired(Duration)}。
     */
    void cleanupExpired(Duration maxIdle, Instant now) {
        Instant cutoff = now.minus(maxIdle);
        removeExpired(pendingImageContexts, pendingImageTimes, cutoff);
        removeExpired(pendingVideoContexts, pendingVideoTimes, cutoff);
        removeExpired(pendingFileContexts, pendingFileTimes, cutoff);
        removeExpired(pendingFileUploads, pendingUploadTimes, cutoff);
    }

    /**
     * 定时清理用户不再续接的媒体上下文，防止内存驻留。
     */
    @Scheduled(fixedDelayString = "${wxclaw.ilink.media-context-cleanup-ms:3600000}")
    public void cleanupExpiredScheduled() {
        if (maxIdle == null || maxIdle.isZero() || maxIdle.isNegative()) {
            return;
        }
        cleanupExpired(maxIdle);
    }

    private <V> void removeExpired(ConcurrentHashMap<String, V> contents,
                                   ConcurrentHashMap<String, Instant> times, Instant cutoff) {
        times.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                contents.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 判断用户意图是否为上传到知识库
     */
    public boolean isKnowledgeBaseUploadIntent(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        if (lower.contains("不要") || lower.contains("不用") || lower.contains("取消")) {
            return false;
        }
        boolean hasDestination = lower.contains("知识库")
                || lower.contains("数据库")
                || lower.contains("资料库")
                || lower.contains("文档库");
        boolean hasUploadAction = lower.contains("上传")
                || lower.contains("导入")
                || lower.contains("加入")
                || lower.contains("保存")
                || lower.contains("存")
                || lower.contains("放到")
                || lower.contains("放入");
        return hasDestination && hasUploadAction || lower.equals("入库");
    }

    /**
     * 待上传文件记录
     */
    public record PendingFileUpload(String fileName, byte[] fileBytes) {}
}
