package com.dust.wxclawbackfront.ilnk.inbound;

import com.dust.wxclawbackfront.ai.chat.ChatHandler;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import com.dust.wxclawbackfront.ai.chat.AIContentAccumulator;
import com.dust.wxclawbackfront.ai.image.ImageGenerationHandler;
import com.dust.wxclawbackfront.ai.image.ImageGenerationIntentDetector;
import com.dust.wxclawbackfront.ai.image.ImageGenerationResult;
import com.dust.wxclawbackfront.ai.voice.VolcTtsHandler;
import com.dust.wxclawbackfront.ai.voice.VolcTtsResult;
import com.dust.wxclawbackfront.ai.voice.VoiceIntentDetector;
import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import com.dust.wxclawbackfront.ai.trace.AiChatTrace;
import com.dust.wxclawbackfront.ai.trace.AiChatTraceStore;
import com.dust.wxclawbackfront.ilnk.ILinkUserInput;
import com.dust.wxclawbackfront.ilnk.ILinkUserInputExtractor;
import com.dust.wxclawbackfront.ilnk.outbound.ILinkMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ILink 入站消息处理器
 * 负责处理收到的用户消息，调用 AI 服务，发送回复
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageDispatcher {

    private static final int MESSAGE_TYPE_USER = 0;
    private static final int MESSAGE_TYPE_ASSISTANT = 1;

    private final AiConversationCrudService crudService;
    private final ChatHandler chatHandler;
    private final ObjectProvider<AIContentAccumulator> accumulatorProvider;
    private final AiChatTraceStore traceStore;
    private final ILinkUserInputExtractor userInputExtractor;
    private final ImageGenerationIntentDetector imageGenerationIntentDetector;
    private final ImageGenerationHandler imageGenerationHandler;
    private final VoiceIntentDetector voiceIntentDetector;
    private final VolcTtsHandler volcTtsHandler;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper;

    @Value("${wxclaw.ai.image.direct-reply:true}")
    private boolean imageDirectReply;

    @Value("${wxclaw.ai.context.max-history-messages:20}")
    private int maxHistoryMessages;

    @Value("${wxclaw.ai.trace.max-field-chars:4000}")
    private int maxTraceFieldChars;

    @Value("${wxclaw.ai.tts.max-reply-chars:220}")
    private int ttsMaxReplyChars;

    /**
     * 处理入站消息
     */
    public void dispatch(WeixinMessage msg) {
        if (msg == null) {
            return;
        }

        String userId = msg.getFrom_user_id();
        String contextToken = msg.getContext_token();
        String sessionId = userId;

        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        // 设置用户上下文
        UserContextHolder.setUserId(userId);
        try {
            processMessage(msg, userId, contextToken, sessionId);
        } finally {
            UserContextHolder.clear();
        }
    }

    private void processMessage(WeixinMessage msg, String userId, String contextToken, String sessionId) {
        ILinkUserInput userInput = userInputExtractor.extract(null, msg);
        if (userInput == null) {
            String trimmed = userInputExtractor.extractText(msg);
            if (trimmed == null || trimmed.isBlank()) {
                return;
            }
            userInput = ILinkUserInput.text(trimmed.trim());
        }

        List<AiMessage> historyMessages = crudService.listMessages(sessionId);
        historyMessages = normalizeHistory(historyMessages, maxHistoryMessages);

        crudService.createOrGetConversation(sessionId, userId);
        crudService.appendMessage(sessionId, MESSAGE_TYPE_USER, userInput.getPersistText(), null, null, null);

        AIContentAccumulator accumulator = accumulatorProvider.getObject();
        Instant start = Instant.now();
        AiChatTrace trace = buildTrace(msg, userId, contextToken, sessionId, userInput);

        String reply;
        try {
            if (shouldGenerateImage(userInput)) {
                handleImageGeneration(userInput, userId, sessionId, trace, accumulator, start);
                return;
            } else if (shouldSendVoice(userInput)) {
                handleVoiceReply(userInput, historyMessages, userId, sessionId, trace, accumulator, start);
                return;
            } else if ("IMAGE".equalsIgnoreCase(userInput.getMessageItemType())
                    && imageDirectReply
                    && userInput.getError() == null
                    && userInput.getImageDescription() != null
                    && !userInput.getImageDescription().isBlank()) {
                reply = userInput.getImageDescription().trim();
                trace.setModel(userInput.getImageModel());
                trace.setLlmRequestJson(clipTrace(userInput.getImageLlmRequestJson()));
            } else if ("IMAGE".equalsIgnoreCase(userInput.getMessageItemType())
                    && userInput.getError() != null
                    && !userInput.getError().isBlank()) {
                reply = "收到图片，但图片理解失败。请尝试重新发送图片或换一张更清晰的图片。\n错误信息：" + userInput.getError().trim();
            } else {
                reply = chatHandler.chat(userInput.getPromptText(), historyMessages, accumulator);
                fillTraceFromAccumulator(trace, accumulator);
            }

            int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
            crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, reply, null, responseTime, null);

            trace.setReplyText(reply);
            trace.setResponseTimeMs(responseTime);
            traceStore.add(trace);

            messageSender.sendText(userId, reply);

        } catch (Exception ex) {
            handleError(ex, userId, sessionId, trace, accumulator, start);
        }
    }

    private void handleImageGeneration(ILinkUserInput userInput, String userId, String sessionId,
                                       AiChatTrace trace, AIContentAccumulator accumulator, Instant start) throws Exception {
        ImageGenerationResult generationResult = imageGenerationHandler.generate(userInput.getDisplayText());
        trace.setModel(generationResult.getModel());
        trace.setGeneratedImageRequestJson(clipTrace(generationResult.getRequestJson()));
        trace.setGeneratedImageResponseJson(clipTrace(generationResult.getResponseJson()));
        trace.setGeneratedImageUrl(clipTrace(TextSanitizer.summarizeDataUrl(generationResult.getImageUrl())));

        if (generationResult.getErrorMsg() != null && !generationResult.getErrorMsg().isBlank()) {
            throw new IllegalStateException("生图失败: " + generationResult.getErrorMsg());
        }

        String reply = imageGenerationHandler.getReplyText();
        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
        crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, "[IMAGE_GENERATED] " + reply, null, responseTime, null);

        trace.setReplyText(reply);
        trace.setResponseTimeMs(responseTime);
        traceStore.add(trace);

        messageSender.sendImage(userId, generationResult.getImageBytes(), generationResult.getFileName(), reply);
    }

    private void handleVoiceReply(ILinkUserInput userInput, List<AiMessage> historyMessages, String userId,
                                  String sessionId, AiChatTrace trace, AIContentAccumulator accumulator, Instant start) throws Exception {
        String voicePrompt = buildVoicePrompt(userInput.getPromptText());
        String reply = chatHandler.chat(voicePrompt, historyMessages, accumulator);
        String spokenText = truncateForVoice(reply);

        fillTraceFromAccumulator(trace, accumulator);

        VolcTtsResult ttsResult = volcTtsHandler.synthesize(spokenText);
        trace.setTtsRequestJson(clipTrace(ttsResult.getRequestJson()));
        trace.setTtsResponseJson(clipTrace(ttsResult.getResponseJson()));
        trace.setTtsPlayTimeMs(ttsResult.getPlayTimeMs());
        trace.setTtsSampleRate(ttsResult.getSampleRate());

        if (ttsResult.getErrorMsg() != null && !ttsResult.getErrorMsg().isBlank()) {
            throw new IllegalStateException("TTS 失败: " + ttsResult.getErrorMsg());
        }

        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
        crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, "[AUDIO_FILE] " + spokenText, null, responseTime, null);

        trace.setReplyText(spokenText);
        trace.setResponseTimeMs(responseTime);

        try {
            messageSender.sendFile(userId, ttsResult.getAudioBytes(), ttsResult.getFileName(), "已生成音频文件，请查收。");
            traceStore.add(trace);
        } catch (Exception sendFileEx) {
            String errorMsg = sendFileEx.getMessage() == null ? sendFileEx.getClass().getSimpleName() : sendFileEx.getMessage();
            trace.setErrorMsg("sendFile failed: " + errorMsg);
            messageSender.sendText(userId, spokenText);
            traceStore.add(trace);
        }
    }

    private void handleError(Exception ex, String userId, String sessionId, AiChatTrace trace,
                            AIContentAccumulator accumulator, Instant start) {
        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
        crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, null, null, responseTime, ex.getMessage());

        fillTraceFromAccumulator(trace, accumulator);
        trace.setResponseTimeMs(responseTime);
        trace.setErrorMsg(ex.getMessage());
        traceStore.add(trace);

        log.warn("处理消息失败: {}", ex.getMessage());
        try {
            String msgToUser = buildErrorMessage(ex);
            messageSender.sendText(userId, msgToUser);
        } catch (Exception ignored) {
        }
    }

    private AiChatTrace buildTrace(WeixinMessage msg, String userId, String contextToken,
                                   String sessionId, ILinkUserInput userInput) {
        AiChatTrace trace = new AiChatTrace();
        trace.setTimestamp(OffsetDateTime.now());
        trace.setSessionId(sessionId);
        trace.setContextToken(contextToken);
        trace.setIlinkMessageJson(clipTrace(toJsonSafely(msg)));
        trace.setMessageItemType(userInput.getMessageItemType());
        trace.setImageUrl(clipTrace(TextSanitizer.summarizeDataUrl(userInput.getImageUrl())));
        trace.setImageModel(userInput.getImageModel());
        trace.setImageDescription(clipTrace(userInput.getImageDescription()));
        trace.setImageLlmRequestJson(clipTrace(userInput.getImageLlmRequestJson()));
        trace.setUserText(userInput.getDisplayText());
        return trace;
    }

    private void fillTraceFromAccumulator(AiChatTrace trace, AIContentAccumulator accumulator) {
        if (trace.getModel() == null || trace.getModel().isBlank()) {
            trace.setModel(accumulator.getModel());
        }
        if (trace.getRequestText() == null || trace.getRequestText().isBlank()) {
            trace.setRequestText(clipTrace(accumulator.getRequestText()));
        }
        if (trace.getLlmRequestJson() == null || trace.getLlmRequestJson().isBlank()) {
            trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
        }
        if (trace.getToolName() == null || trace.getToolName().isBlank()) {
            trace.setToolName(accumulator.getToolName());
        }
        if (trace.getToolRequest() == null || trace.getToolRequest().isBlank()) {
            trace.setToolRequest(clipTrace(accumulator.getToolRequest()));
        }
        if (trace.getToolResponse() == null || trace.getToolResponse().isBlank()) {
            trace.setToolResponse(clipTrace(accumulator.getToolResponse()));
        }
        if (trace.getAgentTraceJson() == null || trace.getAgentTraceJson().isBlank()) {
            trace.setAgentTraceJson(clipTrace(accumulator.getAgentTraceJson()));
        }
        if (trace.getAgentRounds() == null) {
            trace.setAgentRounds(accumulator.getAgentRounds());
        }
        if (trace.getAgentCompleted() == null) {
            trace.setAgentCompleted(accumulator.getAgentCompleted());
        }
    }

    private String buildErrorMessage(Exception ex) {
        String msgToUser = "处理失败，请稍后再试。";
        String em = ex.getMessage() == null ? "" : ex.getMessage();
        if (em.contains("TTS") || em.contains("tts") || em.contains("语音")) {
            if (em.contains("未配置")) {
                msgToUser = "语音功能暂未配置完成，请稍后再试。";
            } else {
                msgToUser = "语音生成失败，请稍后再试。";
            }
        } else if (em.contains("生图")) {
            msgToUser = "图片生成失败，请稍后再试。";
        }
        return msgToUser;
    }

    private boolean shouldGenerateImage(ILinkUserInput userInput) {
        if (userInput == null || !"TEXT".equalsIgnoreCase(userInput.getMessageItemType())) {
            return false;
        }
        return imageGenerationIntentDetector.isImageGenerationIntent(userInput.getDisplayText());
    }

    private boolean shouldSendVoice(ILinkUserInput userInput) {
        if (userInput == null || !"TEXT".equalsIgnoreCase(userInput.getMessageItemType())) {
            return false;
        }
        return voiceIntentDetector.isVoiceIntent(userInput.getDisplayText());
    }

    private String buildVoicePrompt(String promptText) {
        String userText = promptText == null ? "" : promptText.trim();
        return "你将把回复内容用于语音播报并直接发送给用户。\n"
                + "请用中文口语化回复，确保适合在 60 秒语音中播报，尽量简短（建议不超过 200 字），不要输出项目符号/列表。\n"
                + "不要提及\"我无法发送语音/没法直接发语音/请你用朗读功能\"等实现限制，也不要引导用户自己朗读。\n\n用户问题：\n"
                + userText;
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

    private static List<AiMessage> normalizeHistory(List<AiMessage> historyMessages, int maxMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiMessage> list = new ArrayList<>(historyMessages);
        list.sort(Comparator.comparing(AiMessage::getMessageSeq, Comparator.nullsLast(Integer::compareTo)));
        int limit = maxMessages <= 0 ? 20 : maxMessages;
        if (list.size() > limit) {
            return list.subList(list.size() - limit, list.size());
        }
        return list;
    }

    private String clipTrace(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int limit = maxTraceFieldChars <= 0 ? 4000 : maxTraceFieldChars;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated,len=" + text.length() + ")";
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
