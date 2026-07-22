package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.llm.chat.ChatHandler;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.agent.llm.voice.VolcTtsHandler;
import com.dust.wxclawbackfront.bot.agent.llm.voice.VolcTtsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 语音合成工具处理器
 * 职责：获取文本 → 润色为口语化 → 合成语音
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceSynthesizeToolHandler implements ToolHandler {

    private final ChatHandler chatHandler;
    private final VolcTtsHandler volcTtsHandler;

    @Value("${wxclaw.ai.tts.max-reply-chars:220}")
    private int ttsMaxReplyChars;

    @Override
    public String getName() {
        return "voice_synthesize";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        Instant start = Instant.now();

        try {
            // 1. 获取待朗读文本：优先用前一步结果，否则让模型生成
            String rawText = getTextFromParams(step);
            if (rawText == null || rawText.isBlank()) {
                rawText = generateVoiceReply(context);
            }

            if (rawText == null || rawText.isBlank()) {
                long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
                return TaskResult.failure("无法生成语音内容", ms);
            }

            // 2. 润色为口语化文本（前一步的结果通常是书面语，需要转换）
            String spokenText = refineForVoice(rawText, context);

            // 3. 截断到 TTS 限制
            spokenText = truncateForVoice(spokenText);

            log.info("语音合成: text={}", spokenText.length() > 50 ? spokenText.substring(0, 50) + "..." : spokenText);

            // 4. TTS 合成
            VolcTtsResult ttsResult = volcTtsHandler.synthesize(spokenText);

            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (ttsResult.getErrorMsg() != null && !ttsResult.getErrorMsg().isBlank()) {
                return TaskResult.failure("语音合成失败: " + ttsResult.getErrorMsg(), executionTimeMs);
            }

            byte[] audioBytes = ttsResult.getAudioBytes();
            if (audioBytes == null || audioBytes.length == 0) {
                return TaskResult.failure("生成的音频数据为空", executionTimeMs);
            }

            return TaskResult.successWithMedia(spokenText, audioBytes,
                    "audio/wav", ttsResult.getFileName(), executionTimeMs);

        } catch (Exception e) {
            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("语音合成异常: {}", e.getMessage());
            return TaskResult.failure("语音合成异常: " + e.getMessage(), executionTimeMs);
        }
    }

    /**
     * 从步骤参数中获取文本（前一步的结果）
     */
    private String getTextFromParams(TaskStep step) {
        if (step.getParams() != null) {
            Object text = step.getParams().get("text");
            if (text instanceof String && !((String) text).isBlank()) {
                return ((String) text).trim();
            }
        }
        return null;
    }

    /**
     * 让模型直接生成语音回复（无前一步结果时）
     */
    private String generateVoiceReply(AgentContext context) {
        try {
            List<AiMessage> historyMessages = context.getHistoryMessages() != null
                    ? context.getHistoryMessages() : Collections.emptyList();

            String prompt = "你将把回复内容用于语音播报并直接发送给用户。\n"
                    + "请用中文口语化回复，确保适合在 60 秒语音中播报，尽量简短（建议不超过 200 字），不要输出项目符号/列表。\n"
                    + "不要提及\"我无法发送语音/没法直接发语音/请你用朗读功能\"等实现限制。\n\n用户问题：\n" + context.getUserText();

            return chatHandler.chat(prompt, historyMessages);
        } catch (Exception e) {
            log.error("生成语音回复失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将文本润色为适合语音播报的口语化文本
     */
    private String refineForVoice(String rawText, AgentContext context) {
        // 如果文本已经很短且口语化，直接使用
        if (rawText.length() <= 100 && !rawText.contains("**") && !rawText.contains("##")) {
            return rawText;
        }

        try {
            List<AiMessage> historyMessages = context.getHistoryMessages() != null
                    ? context.getHistoryMessages() : Collections.emptyList();

            String prompt = "请将以下内容转换为适合语音播报的口语化文本。要求：\n"
                    + "1. 去掉 Markdown 格式（标题、加粗、列表符号等）\n"
                    + "2. 语言自然口语化，像朋友聊天一样\n"
                    + "3. 简洁明了，不超过 200 字\n"
                    + "4. 保留关键信息（数字、地点、时间等）\n"
                    + "5. 不要加任何前缀说明，直接输出转换后的文本\n\n"
                    + "原文：\n" + rawText;

            String refined = chatHandler.chat(prompt, historyMessages);
            return (refined != null && !refined.isBlank()) ? refined.trim() : rawText;
        } catch (Exception e) {
            log.warn("文本润色失败，使用原文: {}", e.getMessage());
            return rawText;
        }
    }

    private String truncateForVoice(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        int limit = ttsMaxReplyChars <= 0 ? 220 : ttsMaxReplyChars;
        if (t.length() <= limit) {
            return t;
        }
        return t.substring(0, limit);
    }
}
