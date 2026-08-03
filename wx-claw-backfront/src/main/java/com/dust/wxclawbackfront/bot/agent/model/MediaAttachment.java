package com.dust.wxclawbackfront.bot.agent.model;

/**
 * 单条媒体附件（图片/音频/视频/文件）
 */
public record MediaAttachment(String mediaType, String mediaFileName, byte[] mediaBytes) {
    public MediaAttachment {
        mediaBytes = mediaBytes == null ? new byte[0] : mediaBytes.clone();
    }

    public boolean hasBytes() {
        return mediaBytes.length > 0;
    }

    @Override
    public byte[] mediaBytes() {
        return mediaBytes.clone();
    }
}