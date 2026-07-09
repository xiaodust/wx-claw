package com.dust.wxclawbackfront.ai.service.voice;

import lombok.Getter;

@Getter
public final class VolcTtsResult {

    private final String requestJson;
    private final String responseJson;
    private final String errorMsg;
    private final byte[] audioBytes;
    private final Integer playTimeMs;
    private final Integer sampleRate;
    private final Integer bitsPerSample;
    private final Integer encodeType;
    private final String fileName;

    public VolcTtsResult(String requestJson,
                         String responseJson,
                         String errorMsg,
                         byte[] audioBytes,
                         Integer playTimeMs,
                         Integer sampleRate,
                         Integer bitsPerSample,
                         Integer encodeType,
                         String fileName) {
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.errorMsg = errorMsg;
        this.audioBytes = audioBytes;
        this.playTimeMs = playTimeMs;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.encodeType = encodeType;
        this.fileName = fileName;
    }
}


