package com.dust.wxclawbackfront.ai.video;

import lombok.Getter;

@Getter
public final class VideoGenerationResult {

    private final boolean success;
    private final byte[] videoBytes;
    private final String videoUrl;
    private final String errorMsg;

    private VideoGenerationResult(boolean success, byte[] videoBytes, String videoUrl, String errorMsg) {
        this.success = success;
        this.videoBytes = videoBytes;
        this.videoUrl = videoUrl;
        this.errorMsg = errorMsg;
    }

    public static VideoGenerationResult success(byte[] videoBytes, String videoUrl) {
        return new VideoGenerationResult(true, videoBytes, videoUrl, null);
    }

    public static VideoGenerationResult successWithUrl(String videoUrl) {
        return new VideoGenerationResult(true, null, videoUrl, null);
    }

    public static VideoGenerationResult failure(String errorMsg) {
        return new VideoGenerationResult(false, null, null, errorMsg);
    }
}
