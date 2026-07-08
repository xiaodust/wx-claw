package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import com.dust.wxclawbackfront.ai.service.ChatHandler;
import com.dust.wxclawbackfront.ai.trace.AiChatTrace;
import com.dust.wxclawbackfront.ai.trace.AiChatTraceStore;
import com.dust.wxclawbackfront.ai.tools.AIContentAccumulator;
import com.dust.wxclawbackfront.ai.tools.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.WeixinMessage;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;
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

    public ILinkBotService(AiConversationCrudService crudService,
                           ChatHandler chatHandler,
                           ObjectProvider<AIContentAccumulator> accumulatorProvider,
                           AiChatTraceStore traceStore,
                           ILinkUserInputExtractor userInputExtractor,
                           ObjectMapper objectMapper,
                           @Value("${wxclaw.ai.image.direct-reply:true}") boolean imageDirectReply,
                           @Value("${wxclaw.ai.context.max-history-messages:20}") int maxHistoryMessages,
                           @Value("${wxclaw.ai.trace.max-field-chars:4000}") int maxTraceFieldChars) {
        this.crudService = crudService;
        this.chatHandler = chatHandler;
        this.accumulatorProvider = accumulatorProvider;
        this.traceStore = traceStore;
        this.userInputExtractor = userInputExtractor;
        this.objectMapper = objectMapper;
        this.imageDirectReply = imageDirectReply;
        this.maxHistoryMessages = maxHistoryMessages;
        this.maxTraceFieldChars = maxTraceFieldChars;
    }

    public void runILinkMonitor() {
        String bufFile = ILinkBufStore.env("BUF_FILE").orElse("sync_buf.dat");
        String token = ILinkBufStore.env("ILINK_TOKEN").orElse("");
        ILinkClient client = ILinkClient.builder().token(token).build();

        String initialBuf = ILinkBufStore.readFile(bufFile).orElse(null);

        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String qrCodeUrl) {
                System.out.println("请扫码: " + qrCodeUrl);
            }

            @Override
            public void onScanned() {
                System.out.println("已扫码，请在微信上确认...");
            }

            @Override
            public void onExpired(int attempt, int maxAttempts) {
                System.out.println("二维码已过期，正在刷新 (" + attempt + "/" + maxAttempts + ")");
            }
        });

        if (!result.isConnected()) {
            System.err.println("登录失败: " + result.getMessage());
            return;
        }

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopFlag.set(true)));

        MonitorOptions options = MonitorOptions.builder()
                .initialBuf(initialBuf)
                .onBufUpdate(buf -> ILinkBufStore.writeFile(bufFile, buf))
                .onError(err -> System.err.println("监听错误: " + err.getMessage()))
                .onSessionExpired(() -> System.err.println("会话已过期，请重新登录"))
                .build();

        client.monitor(msg -> onMessage(client, msg), options, stopFlag);
    }

    private void onMessage(ILinkClient client, WeixinMessage msg) {
        if (msg == null) {
            return;
        }

        String userId = msg.getFromUserId();
        String contextToken = msg.getContextToken();
        if (userId != null && !userId.isBlank() && contextToken != null && !contextToken.isBlank()) {
            client.setContextToken(userId, contextToken);
        }

        String sessionId = userId;
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        ILinkUserInput userInput = userInputExtractor.extract(msg);
        if (userInput == null) {
            String trimmed = MessageHelper.extractText(msg);
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
        trace.setImageLocalPath(userInput.getImageLocalPath());
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

            if (contextToken != null && !contextToken.isBlank()) {
                client.sendText(userId, reply, contextToken);
            } else {
                client.push(userId, reply);
            }
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
