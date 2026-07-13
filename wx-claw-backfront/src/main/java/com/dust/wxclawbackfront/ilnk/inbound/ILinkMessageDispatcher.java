package com.dust.wxclawbackfront.ilnk.inbound;

import com.dust.wxclawbackfront.ai.chat.ChatHandler;
import com.dust.wxclawbackfront.ai.chat.CommandHandler;
import com.dust.wxclawbackfront.ai.dao.entity.AiConversation;
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
import com.dust.wxclawbackfront.ilnk.runtime.ILinkRuntimeManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.ILinkClient;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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

    private static final ScheduledExecutorService WAIT_NOTICE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "ai-wait-notice");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    // 异步保存消息和trace的线程池
    private static final ExecutorService ASYNC_SAVE_EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "async-save");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final AiConversationCrudService crudService;
    private final ChatHandler chatHandler;
    private final CommandHandler commandHandler;
    private final ObjectProvider<AIContentAccumulator> accumulatorProvider;
    private final AiChatTraceStore traceStore;
    private final ILinkUserInputExtractor userInputExtractor;
    private final ImageGenerationIntentDetector imageGenerationIntentDetector;
    private final ImageGenerationHandler imageGenerationHandler;
    private final VoiceIntentDetector voiceIntentDetector;
    private final VolcTtsHandler volcTtsHandler;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper;
    private final ILinkRuntimeManager runtimeManager;

    @Value("${wxclaw.ai.image.direct-reply:true}")
    private boolean imageDirectReply;

    @Value("${wxclaw.ai.context.max-history-messages:12}")
    private int maxHistoryMessages;

    @Value("${wxclaw.ai.trace.max-field-chars:4000}")
    private int maxTraceFieldChars;

    @Value("${wxclaw.ai.tts.max-reply-chars:220}")
    private int ttsMaxReplyChars;

    @Value("${wxclaw.ai.wait-notice.enabled:true}")
    private boolean waitNoticeEnabled;

    @Value("${wxclaw.ai.wait-notice.delay-seconds:5}")
    private int waitNoticeDelaySeconds;

    @Value("${wxclaw.ai.wait-notice.text:我正在处理中，可能还需要几秒，请稍等一下。}")
    private String waitNoticeText;

    /**
     * 处理入站消息
     */
    public void dispatch(WeixinMessage msg) {
        if (msg == null) {
            return;
        }

        String userId = msg.getFrom_user_id();
        String contextToken = msg.getContext_token();

        if (userId == null || userId.isBlank()) {
            return;
        }

        // 设置用户上下文
        UserContextHolder.setUserId(userId);
        try {
            // 检查是否是新建对话指令
            String userText = userInputExtractor.extractText(msg);
            if (isNewConversationIntent(userText)) {
                handleNewConversation(msg, userId, contextToken);
                return;
            }

            // 检查是否是 # 命令
            if (commandHandler.isCommand(userText)) {
                handleCommand(userText, userId);
                return;
            }

            // 获取或创建当前用户的活跃会话
            AiConversation activeConversation = crudService.getOrCreateActiveConversation(userId);
            String sessionId = activeConversation.getSessionId();

            processMessage(msg, userId, contextToken, sessionId);
        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isNewConversationIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim().toLowerCase();
        return trimmed.equals("新建对话") 
                || trimmed.equals("新对话") 
                || trimmed.equals("开启新对话")
                || trimmed.equals("清空上下文")
                || trimmed.equals("重新开始");
    }

    private void handleNewConversation(WeixinMessage msg, String userId, String contextToken) {
        try {
            AiConversation newConversation = crudService.createNewConversation(userId);
            String reply = "已为你创建新对话。之前的对话历史已保存，需要时可以通过对话列表查看。";
            
            crudService.appendMessage(newConversation.getSessionId(), MESSAGE_TYPE_ASSISTANT, reply, null, 0, null);
            messageSender.sendText(userId, reply);
            
            log.info("用户新建对话: userId={}, newSessionId={}", userId, newConversation.getSessionId());
        } catch (Exception ex) {
            log.error("新建对话失败: userId={}, error={}", userId, ex.getMessage(), ex);
            try {
                messageSender.sendText(userId, "新建对话失败，请稍后再试。");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 处理 # 命令
     */
    private void handleCommand(String commandText, String userId) {
        try {
            String reply = commandHandler.handle(commandText);
            if (reply != null) {
                messageSender.sendText(userId, reply);
                log.info("处理命令: userId={}, command={}", userId, commandText);
            }
        } catch (Exception ex) {
            log.error("处理命令失败: userId={}, command={}, error={}", userId, commandText, ex.getMessage(), ex);
            try {
                messageSender.sendText(userId, "命令处理失败，请稍后再试。");
            } catch (Exception ignored) {
            }
        }
    }

    private void processMessage(WeixinMessage msg, String userId, String contextToken, String sessionId) {
        ILinkClient client = runtimeManager.getActiveClient();
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
        AiChatTrace trace = buildTrace(msg, userId, contextToken, sessionId, userInput);
        ScheduledFuture<?> waitNoticeFuture = scheduleWaitNotice(userId);

        String reply;
        try {
            if (shouldGenerateImage(userInput)) {
                handleImageGeneration(userInput, historyMessages, userId, sessionId, trace, accumulator, start);
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

            // 先发送消息给用户（减少用户感知的延迟）
            messageSender.sendText(userId, reply);

            // 异步保存消息和trace
            final String finalReply = reply;
            final int finalResponseTime = responseTime;
            CompletableFuture.runAsync(() -> {
                try {
                    crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, finalReply, null, finalResponseTime, null);
                    trace.setReplyText(finalReply);
                    trace.setResponseTimeMs(finalResponseTime);
                    traceStore.add(trace);
                } catch (Exception e) {
                    log.warn("异步保存消息失败: {}", e.getMessage());
                }
            }, ASYNC_SAVE_EXECUTOR);

        } catch (Exception ex) {
            handleError(ex, userId, sessionId, trace, accumulator, start);
        } finally {
            cancelWaitNotice(waitNoticeFuture);
        }
    }

    private void handleImageGeneration(ILinkUserInput userInput, List<AiMessage> historyMessages, String userId,
                                       String sessionId, AiChatTrace trace, AIContentAccumulator accumulator, Instant start) throws Exception {
        // 构建生图prompt，如果是"重画"等简短指令，需要从历史消息中提取之前的描述
        String imagePrompt = buildImagePrompt(userInput.getDisplayText(), historyMessages);
        ImageGenerationResult generationResult = imageGenerationHandler.generate(imagePrompt);
        trace.setModel(generationResult.getModel());
        trace.setGeneratedImageRequestJson(clipTrace(generationResult.getRequestJson()));
        trace.setGeneratedImageResponseJson(clipTrace(generationResult.getResponseJson()));
        trace.setGeneratedImageUrl(clipTrace(TextSanitizer.summarizeDataUrl(generationResult.getImageUrl())));

        if (generationResult.getErrorMsg() != null && !generationResult.getErrorMsg().isBlank()) {
            throw new IllegalStateException("生图失败: " + generationResult.getErrorMsg());
        }

        // 检查图片是否有效
        byte[] imageBytes = generationResult.getImageBytes();
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("生图失败: 生成的图片数据为空");
        }

        String reply = imageGenerationHandler.getReplyText();
        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();

        // 先发送图片给用户
        messageSender.sendImage(userId, imageBytes, generationResult.getFileName(), reply);

        // 异步保存消息和trace
        final String finalReply = "[IMAGE_GENERATED] " + reply;
        final int finalResponseTime = responseTime;
        CompletableFuture.runAsync(() -> {
            try {
                crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, finalReply, null, finalResponseTime, null);
                trace.setReplyText(reply);
                trace.setResponseTimeMs(finalResponseTime);
                traceStore.add(trace);
            } catch (Exception e) {
                log.warn("异步保存图片生成记录失败: {}", e.getMessage());
            }
        }, ASYNC_SAVE_EXECUTOR);
    }

    private void handleVoiceReply(ILinkUserInput userInput, List<AiMessage> historyMessages, String userId,
                                  String sessionId, AiChatTrace trace, AIContentAccumulator accumulator, Instant start) throws Exception {
        // 构建语音prompt，如果是"再说一遍"等简短指令，需要从历史消息中提取之前的问题
        String voicePrompt = buildVoicePrompt(userInput.getPromptText(), historyMessages);
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

        // 先发送语音给用户
        try {
            messageSender.sendFile(userId, ttsResult.getAudioBytes(), ttsResult.getFileName(), "已生成音频文件，请查收。");
        } catch (Exception sendFileEx) {
            String errorMsg = sendFileEx.getMessage() == null ? sendFileEx.getClass().getSimpleName() : sendFileEx.getMessage();
            trace.setErrorMsg("sendFile failed: " + errorMsg);
            messageSender.sendText(userId, spokenText);
        }

        // 异步保存消息和trace
        final String finalReply = "[AUDIO_FILE] " + spokenText;
        final int finalResponseTime = responseTime;
        final String finalSpokenText = spokenText;
        CompletableFuture.runAsync(() -> {
            try {
                crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, finalReply, null, finalResponseTime, null);
                trace.setReplyText(finalSpokenText);
                trace.setResponseTimeMs(finalResponseTime);
                traceStore.add(trace);
            } catch (Exception e) {
                log.warn("异步保存语音记录失败: {}", e.getMessage());
            }
        }, ASYNC_SAVE_EXECUTOR);
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

    /**
     * 构建生图prompt
     * 如果是"重画"、"再画"等简短指令，从历史消息中提取之前的生图描述作为上下文
     */
    private String buildImagePrompt(String userText, List<AiMessage> historyMessages) {
        if (userText == null) {
            return "";
        }
        String trimmed = userText.trim();

        // 判断是否是"重画"类的简短指令
        boolean isRedrawCommand = trimmed.matches("^(重画|重新画|再画|再生成|再来一张|给我重画|帮我重画).*$");

        if (isRedrawCommand && historyMessages != null && !historyMessages.isEmpty()) {
            // 从历史消息中查找最近的用户生图请求
            String lastImageRequest = findLastImageRequest(historyMessages);
            if (lastImageRequest != null && !lastImageRequest.isBlank()) {
                log.info("重画场景：从历史消息中提取到之前的生图描述: {}", lastImageRequest);
                return lastImageRequest;
            }
        }

        return trimmed;
    }

    /**
     * 从历史消息中查找最近的用户生图请求
     */
    private String findLastImageRequest(List<AiMessage> historyMessages) {
        // 从后往前遍历，找到最近的用户消息（非AI回复）
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            AiMessage msg = historyMessages.get(i);
            // 只看用户消息
            if (msg.getMessageType() == MESSAGE_TYPE_USER && msg.getContent() != null) {
                String content = msg.getContent().trim();
                // 跳过"重画"等简短指令本身
                if (content.matches("^(重画|重新画|再画|再生成|再来一张|给我重画|帮我重画).*$")) {
                    continue;
                }
                // 检查是否是生图请求
                if (imageGenerationIntentDetector.isImageGenerationIntent(content)) {
                    return content;
                }
                // 如果是普通文本且包含描述性内容（可能用户先描述再要求画）
                if (content.length() > 10) {
                    return content;
                }
            }
        }
        return null;
    }

    private String buildVoicePrompt(String promptText, List<AiMessage> historyMessages) {
        String userText = promptText == null ? "" : promptText.trim();

        // 判断是否是"再说一遍"类的简短指令
        boolean isRepeatCommand = userText.matches("^(再说一遍|重新说|再讲一遍|再回复一遍|再说一次|重复一遍|语音再说一遍).*$");

        if (isRepeatCommand && historyMessages != null && !historyMessages.isEmpty()) {
            // 从历史消息中查找最近的用户问题
            String lastUserQuestion = findLastUserQuestion(historyMessages);
            if (lastUserQuestion != null && !lastUserQuestion.isBlank()) {
                log.info("语音重说场景：从历史消息中提取到之前的问题: {}", lastUserQuestion);
                userText = lastUserQuestion;
            }
        }

        return "你将把回复内容用于语音播报并直接发送给用户。\n"
                + "请用中文口语化回复，确保适合在 60 秒语音中播报，尽量简短（建议不超过 200 字），不要输出项目符号/列表。\n"
                + "不要提及\"我无法发送语音/没法直接发语音/请你用朗读功能\"等实现限制，也不要引导用户自己朗读。\n\n用户问题：\n"
                + userText;
    }

    /**
     * 从历史消息中查找最近的用户问题（非语音指令）
     */
    private String findLastUserQuestion(List<AiMessage> historyMessages) {
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            AiMessage msg = historyMessages.get(i);
            if (msg.getMessageType() == MESSAGE_TYPE_USER && msg.getContent() != null) {
                String content = msg.getContent().trim();
                // 跳过"再说一遍"等简短指令
                if (content.matches("^(再说一遍|重新说|再讲一遍|再回复一遍|再说一次|重复一遍|语音再说一遍).*$")) {
                    continue;
                }
                // 跳过太短的消息
                if (content.length() < 3) {
                    continue;
                }
                return content;
            }
        }
        return null;
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

    private ScheduledFuture<?> scheduleWaitNotice(String userId) {
        if (!waitNoticeEnabled || userId == null || userId.isBlank()) {
            return null;
        }
        int delay = waitNoticeDelaySeconds <= 0 ? 5 : waitNoticeDelaySeconds;
        String text = (waitNoticeText == null || waitNoticeText.isBlank())
                ? "我正在处理中，可能还需要几秒，请稍等一下。"
                : waitNoticeText.trim();
        return WAIT_NOTICE_EXECUTOR.schedule(() -> {
            try {
                messageSender.sendText(userId, text);
            } catch (Exception ex) {
                log.debug("发送等待提示失败: userId={}, error={}", userId, ex.getMessage());
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void cancelWaitNotice(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
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
}
