package com.dust.wxclawbackfront.bot.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行结果
 */
@Data
@Builder
public class AgentResult {

    /**
     * 文本回复
     */
    private String replyText;

    /**
     * 媒体数据（图片/音频）
     */
    private byte[] mediaBytes;

    /**
     * 媒体类型（image/png, audio/wav 等）
     */
    private String mediaType;

    /**
     * 媒体文件名
     */
    private String mediaFileName;
    private List<MediaAttachment> mediaAttachments;

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 已执行的步骤描述（用于 trace）
     */
    private List<String> executedSteps;

    /**
     * 是否包含媒体数据
     */
    public boolean hasMedia() {
        return mediaBytes != null && mediaBytes.length > 0
                || mediaAttachments != null && mediaAttachments.stream().anyMatch(MediaAttachment::hasBytes);
    }

    /**
     * 创建成功结果（纯文本）
     */
    public static AgentResult success(String replyText) {
        return AgentResult.builder()
                .replyText(replyText)
                .success(true)
                .build();
    }

    /**
     * 创建成功结果（带媒体）
     */
    public static AgentResult successWithMedia(String replyText, byte[] mediaBytes,
                                                String mediaType, String mediaFileName) {
        return AgentResult.builder()
                .replyText(replyText)
                .mediaBytes(mediaBytes)
                .mediaType(mediaType)
                .mediaFileName(mediaFileName)
                .success(true)
                .build();
    }

    /**
     * 创建包含多条媒体附件的结果。
     */
    public static AgentResult successWithMedia(String replyText, List<MediaAttachment> mediaAttachments) {
        return AgentResult.builder()
                .replyText(replyText)
                .mediaAttachments(mediaAttachments == null ? new ArrayList<>() : List.copyOf(mediaAttachments))
                .success(true)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static AgentResult failure(String errorMessage) {
        return AgentResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
