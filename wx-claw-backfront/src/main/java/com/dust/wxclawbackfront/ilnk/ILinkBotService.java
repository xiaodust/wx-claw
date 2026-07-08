package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import com.dust.wxclawbackfront.ai.service.ChatHandler;
import com.dust.wxclawbackfront.ai.tools.chat.AIContentAccumulator;
import com.dust.wxclawbackfront.ai.tools.image.ImageGenerationHandler;
import com.dust.wxclawbackfront.ai.tools.image.ImageGenerationIntentDetector;
import com.dust.wxclawbackfront.ai.tools.image.ImageGenerationResult;
import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.ai.tools.time.TimeHandler;
import com.dust.wxclawbackfront.ai.tools.time.TimeIntentDetector;
import com.dust.wxclawbackfront.ai.tools.time.TimeResult;
import com.dust.wxclawbackfront.ai.tools.voice.VolcTtsHandler;
import com.dust.wxclawbackfront.ai.tools.voice.VolcTtsResult;
import com.dust.wxclawbackfront.ai.tools.voice.VoiceIntentDetector;
import com.dust.wxclawbackfront.ai.tools.weather.SeniverseWeatherHandler;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherIntentDetector;
import com.dust.wxclawbackfront.ai.tools.weather.WeatherNowResult;
import com.dust.wxclawbackfront.ai.trace.AiChatTrace;
import com.dust.wxclawbackfront.ai.trace.AiChatTraceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ILinkBotService {

    private static final Logger log = LoggerFactory.getLogger(ILinkBotService.class);
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
    private final TimeIntentDetector timeIntentDetector;
    private final TimeHandler timeHandler;
    private final WeatherIntentDetector weatherIntentDetector;
    private final SeniverseWeatherHandler weatherHandler;
    private final ObjectMapper objectMapper;
    private final boolean imageDirectReply;
    private final int maxHistoryMessages;
    private final int maxTraceFieldChars;
    private final int ttsMaxReplyChars;
    private final long ilinkConnectTimeoutMs;
    private final long ilinkReadTimeoutMs;
    private final long ilinkWriteTimeoutMs;
    private final long ilinkLoginTimeoutMs;
    private final long ilinkPollIdleMs;

    public ILinkBotService(AiConversationCrudService crudService,
                           ChatHandler chatHandler,
                           ObjectProvider<AIContentAccumulator> accumulatorProvider,
                           AiChatTraceStore traceStore,
                           ILinkUserInputExtractor userInputExtractor,
                           ImageGenerationIntentDetector imageGenerationIntentDetector,
                           ImageGenerationHandler imageGenerationHandler,
                           VoiceIntentDetector voiceIntentDetector,
                           VolcTtsHandler volcTtsHandler,
                           TimeIntentDetector timeIntentDetector,
                           TimeHandler timeHandler,
                           WeatherIntentDetector weatherIntentDetector,
                           SeniverseWeatherHandler weatherHandler,
                           ObjectMapper objectMapper,
                           @Value("${wxclaw.ai.image.direct-reply:true}") boolean imageDirectReply,
                           @Value("${wxclaw.ai.context.max-history-messages:20}") int maxHistoryMessages,
                           @Value("${wxclaw.ai.trace.max-field-chars:4000}") int maxTraceFieldChars,
                           @Value("${wxclaw.ai.tts.max-reply-chars:220}") int ttsMaxReplyChars,
                           @Value("${wxclaw.ilink.connect-timeout-ms:15000}") long ilinkConnectTimeoutMs,
                           @Value("${wxclaw.ilink.read-timeout-ms:35000}") long ilinkReadTimeoutMs,
                           @Value("${wxclaw.ilink.write-timeout-ms:15000}") long ilinkWriteTimeoutMs,
                           @Value("${wxclaw.ilink.login-timeout-ms:180000}") long ilinkLoginTimeoutMs,
                           @Value("${wxclaw.ilink.poll-idle-ms:200}") long ilinkPollIdleMs) {
        this.crudService = crudService;
        this.chatHandler = chatHandler;
        this.accumulatorProvider = accumulatorProvider;
        this.traceStore = traceStore;
        this.userInputExtractor = userInputExtractor;
        this.imageGenerationIntentDetector = imageGenerationIntentDetector;
        this.imageGenerationHandler = imageGenerationHandler;
        this.voiceIntentDetector = voiceIntentDetector;
        this.volcTtsHandler = volcTtsHandler;
        this.timeIntentDetector = timeIntentDetector;
        this.timeHandler = timeHandler;
        this.weatherIntentDetector = weatherIntentDetector;
        this.weatherHandler = weatherHandler;
        this.objectMapper = objectMapper;
        this.imageDirectReply = imageDirectReply;
        this.maxHistoryMessages = maxHistoryMessages;
        this.maxTraceFieldChars = maxTraceFieldChars;
        this.ttsMaxReplyChars = ttsMaxReplyChars;
        this.ilinkConnectTimeoutMs = ilinkConnectTimeoutMs;
        this.ilinkReadTimeoutMs = ilinkReadTimeoutMs;
        this.ilinkWriteTimeoutMs = ilinkWriteTimeoutMs;
        this.ilinkLoginTimeoutMs = ilinkLoginTimeoutMs;
        this.ilinkPollIdleMs = ilinkPollIdleMs;
    }

    public void runILinkMonitor() {
        ILinkConfig config = ILinkConfig.builder()
                .connectTimeoutMs(ilinkConnectTimeoutMs)
                .readTimeoutMs(ilinkReadTimeoutMs)
                .writeTimeoutMs(ilinkWriteTimeoutMs)
                .loginTimeoutMs(ilinkLoginTimeoutMs)
                .heartbeatEnabled(false)
                .build();

        ILinkClient client = ILinkClient.builder()
                .config(config)
                .build();
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopFlag.set(true);
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }));

        try {
            String qrCodeContent = client.executeLogin();
            log.info("请扫码登录：\n{}", qrCodeContent);
            client.getLoginFuture().get();
            log.info("iLink 登录成功，开始监听消息...");

            while (!stopFlag.get()) {
                try {
                    List<WeixinMessage> messages = client.getUpdates();
                    if (messages != null) {
                        for (WeixinMessage msg : messages) {
                            onMessage(client, msg);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("监听错误: {}", ex.getMessage());
                    sleepQuietly(1000L);
                }
                sleepQuietly(ilinkPollIdleMs);
            }
        } catch (Exception ex) {
            log.error("iLink 启动失败: {}", ex.getMessage());
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void onMessage(ILinkClient client, WeixinMessage msg) {
        if (msg == null) {
            return;
        }

        String userId = msg.getFrom_user_id();
        String contextToken = msg.getContext_token();
        String sessionId = userId;
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        ILinkUserInput userInput = userInputExtractor.extract(client, msg);
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

        String reply;
        try {
            if (shouldGenerateImage(userInput)) {
                ImageGenerationResult generationResult = imageGenerationHandler.generate(userInput.getDisplayText());
                trace.setModel(generationResult.getModel());
                trace.setGeneratedImageRequestJson(clipTrace(generationResult.getRequestJson()));
                trace.setGeneratedImageResponseJson(clipTrace(generationResult.getResponseJson()));
                trace.setGeneratedImageUrl(clipTrace(TextSanitizer.summarizeDataUrl(generationResult.getImageUrl())));
                if (generationResult.getErrorMsg() != null && !generationResult.getErrorMsg().isBlank()) {
                    throw new IllegalStateException("生图失败: " + generationResult.getErrorMsg());
                }
                reply = imageGenerationHandler.getReplyText();
                int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
                crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, "[IMAGE_GENERATED] " + reply, null, responseTime, null);

                trace.setReplyText(reply);
                trace.setResponseTimeMs(responseTime);
                traceStore.add(trace);

                client.sendImage(userId,
                        generationResult.getImageBytes(),
                        generationResult.getFileName(),
                        reply);
                return;
            } else if (shouldSendVoice(userInput)) {
                ToolReply toolReply = tryBuildToolReply(userInput, true);
                String spokenText;
                if (toolReply != null) {
                    spokenText = truncateForVoice(toolReply.replyText());
                    trace.setToolName(toolReply.toolName());
                    trace.setToolRequest(clipTrace(toolReply.toolRequest()));
                    trace.setToolResponse(clipTrace(toolReply.toolResponse()));
                } else {
                    String voicePrompt = buildVoicePrompt(userInput.getPromptText());
                    reply = chatHandler.chat(voicePrompt, historyMessages, accumulator);
                    spokenText = truncateForVoice(reply);
                    trace.setModel(accumulator.getModel());
                    trace.setRequestText(clipTrace(accumulator.getRequestText()));
                    trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
                }

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
                    client.sendFile(userId, ttsResult.getAudioBytes(), ttsResult.getFileName(), "已生成音频文件，请查收。");
                    traceStore.add(trace);
                } catch (Exception sendFileEx) {
                    String errorMsg = sendFileEx.getMessage() == null ? sendFileEx.getClass().getSimpleName() : sendFileEx.getMessage();
                    trace.setErrorMsg("sendFile failed: " + errorMsg);
                    try {
                        client.sendText(userId, spokenText);
                        traceStore.add(trace);
                        return;
                    } catch (Exception sendTextEx) {
                        throw sendTextEx;
                    }
                }
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
                ToolReply toolReply = tryBuildToolReply(userInput, false);
                if (toolReply != null) {
                    reply = toolReply.replyText();
                    trace.setToolName(toolReply.toolName());
                    trace.setToolRequest(clipTrace(toolReply.toolRequest()));
                    trace.setToolResponse(clipTrace(toolReply.toolResponse()));
                } else {
                    reply = chatHandler.chat(userInput.getPromptText(), historyMessages, accumulator);
                    trace.setModel(accumulator.getModel());
                    trace.setRequestText(clipTrace(accumulator.getRequestText()));
                    trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
                }
            }
            int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
            crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, reply, null, responseTime, null);

            trace.setReplyText(reply);
            trace.setResponseTimeMs(responseTime);
            traceStore.add(trace);

            client.sendText(userId, reply);
        } catch (Exception ex) {
            int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
            crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, null, null, responseTime, ex.getMessage());

            if (trace.getModel() == null || trace.getModel().isBlank()) {
                trace.setModel(accumulator.getModel());
            }
            if (trace.getRequestText() == null || trace.getRequestText().isBlank()) {
                trace.setRequestText(clipTrace(accumulator.getRequestText()));
            }
            if (trace.getLlmRequestJson() == null || trace.getLlmRequestJson().isBlank()) {
                trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
            }
            trace.setResponseTimeMs(responseTime);
            trace.setErrorMsg(ex.getMessage());
            traceStore.add(trace);

            log.warn("发送失败: {}", ex.getMessage());
            try {
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
                client.sendText(userId, msgToUser);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldGenerateImage(ILinkUserInput userInput) {
        if (userInput == null) {
            return false;
        }
        if (!"TEXT".equalsIgnoreCase(userInput.getMessageItemType())) {
            return false;
        }
        return imageGenerationIntentDetector.isImageGenerationIntent(userInput.getDisplayText());
    }

    private boolean shouldSendVoice(ILinkUserInput userInput) {
        if (userInput == null) {
            return false;
        }
        if (!"TEXT".equalsIgnoreCase(userInput.getMessageItemType())) {
            return false;
        }
        return voiceIntentDetector.isVoiceIntent(userInput.getDisplayText());
    }

    private String buildVoicePrompt(String promptText) {
        String userText = promptText == null ? "" : promptText.trim();
        return "你将把回复内容用于语音播报并直接发送给用户。\n"
                + "请用中文口语化回复，确保适合在 60 秒语音中播报，尽量简短（建议不超过 200 字），不要输出项目符号/列表。\n"
                + "不要提及“我无法发送语音/没法直接发语音/请你用朗读功能”等实现限制，也不要引导用户自己朗读。\n\n用户问题：\n"
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

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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

    private ToolReply tryBuildToolReply(ILinkUserInput userInput, boolean forVoice) {
        if (userInput == null) {
            return null;
        }
        if (!"TEXT".equalsIgnoreCase(userInput.getMessageItemType())) {
            return null;
        }
        String text = userInput.getDisplayText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (timeIntentDetector != null && timeIntentDetector.isTimeIntent(text)) {
            TimeResult timeResult = timeHandler == null ? null : timeHandler.now();
            String replyText = timeResult == null ? null : timeResult.getReplyText();
            if (replyText == null || replyText.isBlank()) {
                replyText = "时间查询失败。";
            }
            String toolRequest = timeResult == null ? null : ("zoneId=" + timeResult.getZoneId());
            String toolResponse = timeResult == null ? null : toJsonSafely(timeResult);
            return new ToolReply("time", toolRequest, toolResponse, replyText);
        }
        if (weatherIntentDetector != null && weatherIntentDetector.isWeatherIntent(text)) {
            String loc = weatherIntentDetector.extractLocationOrNull(text);
            WeatherNowResult result = weatherHandler == null ? null : weatherHandler.now(loc);
            String replyText = forVoice ? (weatherHandler == null ? null : weatherHandler.formatReplyForVoice(result))
                    : (weatherHandler == null ? null : weatherHandler.formatReply(result));
            if (replyText == null || replyText.isBlank()) {
                replyText = "天气查询失败。";
            }
            String toolRequest = result == null ? null : result.getRequestUrl();
            String toolResponse = result == null ? null : result.getResponseJson();
            return new ToolReply("weather", toolRequest, toolResponse, replyText);
        }
        return null;
    }

    private record ToolReply(String toolName, String toolRequest, String toolResponse, String replyText) {
    }
}
