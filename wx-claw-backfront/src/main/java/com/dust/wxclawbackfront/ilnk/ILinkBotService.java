package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import com.dust.wxclawbackfront.ai.service.ChatHandler;
import com.dust.wxclawbackfront.ai.trace.AiChatTrace;
import com.dust.wxclawbackfront.ai.trace.AiChatTraceStore;
import com.dust.wxclawbackfront.ai.tools.AIContentAccumulator;
import com.dust.wxclawbackfront.ai.tools.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
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

    private static final int MESSAGE_TYPE_USER = 0;
    private static final int MESSAGE_TYPE_ASSISTANT = 1;

    private final AiConversationCrudService crudService;
    private final ChatHandler chatHandler;
    private final ObjectProvider<AIContentAccumulator> accumulatorProvider;
    private final AiChatTraceStore traceStore;
    private final ILinkUserInputExtractor userInputExtractor;
    private final ObjectMapper objectMapper;
    private final boolean imageDirectReply;
    private final int maxHistoryMessages;
    private final int maxTraceFieldChars;
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
                           ObjectMapper objectMapper,
                           @Value("${wxclaw.ai.image.direct-reply:true}") boolean imageDirectReply,
                           @Value("${wxclaw.ai.context.max-history-messages:20}") int maxHistoryMessages,
                           @Value("${wxclaw.ai.trace.max-field-chars:4000}") int maxTraceFieldChars,
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
        this.objectMapper = objectMapper;
        this.imageDirectReply = imageDirectReply;
        this.maxHistoryMessages = maxHistoryMessages;
        this.maxTraceFieldChars = maxTraceFieldChars;
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
            System.out.println("请扫码登录：");
            System.out.println(qrCodeContent);
            client.getLoginFuture().get();
            System.out.println("iLink 登录成功，开始监听消息...");

            while (!stopFlag.get()) {
                try {
                    List<WeixinMessage> messages = client.getUpdates();
                    if (messages != null) {
                        for (WeixinMessage msg : messages) {
                            onMessage(client, msg);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("监听错误: " + ex.getMessage());
                    sleepQuietly(1000L);
                }
                sleepQuietly(ilinkPollIdleMs);
            }
        } catch (Exception ex) {
            System.err.println("iLink 启动失败: " + ex.getMessage());
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
            if ("IMAGE".equalsIgnoreCase(userInput.getMessageItemType())
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
                trace.setModel(accumulator.getModel());
                trace.setRequestText(clipTrace(accumulator.getRequestText()));
                trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
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

            trace.setModel(accumulator.getModel());
            trace.setRequestText(clipTrace(accumulator.getRequestText()));
            trace.setLlmRequestJson(clipTrace(accumulator.getLlmRequestJson()));
            trace.setResponseTimeMs(responseTime);
            trace.setErrorMsg(ex.getMessage());
            traceStore.add(trace);

            System.err.println("发送失败: " + ex.getMessage());
        }
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
}
