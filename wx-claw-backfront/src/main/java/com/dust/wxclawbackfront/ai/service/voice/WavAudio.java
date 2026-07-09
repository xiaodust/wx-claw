package com.dust.wxclawbackfront.ai.service.voice;

public record WavAudio(int sampleRate, int bitsPerSample, int channels, int durationMs, byte[] bytes) {
}


