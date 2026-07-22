package com.dust.wxclawbackfront.bot.agent.llm.voice;

public record WavAudio(int sampleRate, int bitsPerSample, int channels, int durationMs, byte[] bytes) {
}


