package com.dust.wxclawbackfront.ai.voice;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class VoiceIntentDetector {

    private static final Pattern VOICE_STRICT_PATTERN = Pattern.compile(
            "(发送(一条|一段|个)?(语音|音频)|发(一条|一段|个)?(语音|音频)|来(一条|一段)?(语音|音频)|" +
            "语音(回复|回答|告诉我|说)|用语音(回复|回答|告诉我|说)|音频(回复|回答|告诉我|说)|用音频(回复|回答|告诉我|说)|" +
            "读出来|朗读(一下)?|念出来|念给我|播报(一下)?|唠一下|说给我听|讲给我听|" +
            "用(语音|声音|音频)(说|讲|回复|回答)(一下)?|给我(发|来|发送)(一条|一段|个)?(语音|音频)|" +
            "(语音|音频)(版|形式|方式)|语音跟我说|语音和我说|语音跟我聊|音频跟我说|音频和我说)",
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
        // 兜底逻辑：包含"语音"或"音频"，且包含动作词
        if (!t.contains("语音") && !t.contains("音频")) {
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
