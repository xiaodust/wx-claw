package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    /**
     * 存储图片上下文
     */
    public void storeImageContext(String userId, String imageDescription) {
        pendingImageContexts.put(userId, imageDescription);
    }

    /**
     * 存储视频上下文
     */
    public void storeVideoContext(String userId, String videoDescription) {
        pendingVideoContexts.put(userId, videoDescription);
    }

    /**
     * 存储文件上下文
     */
    public void storeFileContext(String userId, ILinkUserInput userInput) {
        String fileInfo = buildFileInfo(userInput);
        pendingFileContexts.put(userId, fileInfo);

        // 存储文件字节，用于后续上传到知识库
        if (userInput.getFileBytes() != null && userInput.getFileBytes().length > 0) {
            pendingFileUploads.put(userId, new PendingFileUpload(
                    userInput.getFileName(), userInput.getFileBytes()));
        }
    }

    /**
     * 获取并移除图片上下文
     */
    public String takeImageContext(String userId) {
        return pendingImageContexts.remove(userId);
    }

    /**
     * 获取并移除视频上下文
     */
    public String takeVideoContext(String userId) {
        return pendingVideoContexts.remove(userId);
    }

    /**
     * 获取并移除文件上下文
     */
    public String takeFileContext(String userId) {
        return pendingFileContexts.remove(userId);
    }

    /**
     * 获取并移除待上传文件
     */
    public PendingFileUpload takePendingFileUpload(String userId) {
        return pendingFileUploads.remove(userId);
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
     * 判断用户意图是否为上传到知识库
     */
    public boolean isKnowledgeBaseUploadIntent(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        return lower.contains("上传") && lower.contains("知识库")
                || lower.contains("导入") && lower.contains("知识库")
                || lower.contains("加入") && lower.contains("知识库")
                || lower.contains("存入") && lower.contains("知识库")
                || lower.equals("上传到知识库")
                || lower.equals("上传知识库")
                || lower.equals("入库");
    }

    /**
     * 待上传文件记录
     */
    public record PendingFileUpload(String fileName, byte[] fileBytes) {}
}
