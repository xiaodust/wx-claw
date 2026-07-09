package com.dust.wxclawbackfront.ai.voice;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class VoiceIntentDetector {

    private static final Pattern VOICE_STRICT_PATTERN = Pattern.compile(
            "(发送语音|发语音|来(一条|一段)?语音|语音(回复|回答)|用语音(回复|回答)|读出来|朗读(一下)?|念出来|播报(一下)?|用(语音|声音)(说|讲)一下|给我(发|来)(一条|一段)?语音)",
            Pattern.CASE_INSENSITIVE);

    public boolean isVoiceIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.contains("语音识别") || t.contains("ASR")) {
            return false;
        }
        if (VOICE_STRICT_PATTERN.matcher(t).find()) {
            return true;
        }
        if (!t.contains("语音")) {
            return false;
        }
        return t.contains("回复")
                || t.contains("回答")
                || t.contains("发")
                || t.contains("来")
                || t.contains("读")
                || t.contains("朗读")
                || t.contains("念")
                || t.contains("播报")
                || t.contains("说")
                || t.contains("讲");
    }
}
