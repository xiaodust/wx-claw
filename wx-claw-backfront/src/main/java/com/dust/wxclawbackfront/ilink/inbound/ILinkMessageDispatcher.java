package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ai.agent.model.AgentContext;
import com.dust.wxclawbackfront.ai.agent.model.AgentResult;
import com.dust.wxclawbackfront.ai.agent.orchestrator.AgentOrchestrator;
import com.dust.wxclawbackfront.ai.chat.CommandHandler;
import com.dust.wxclawbackfront.ai.dao.entity.AiConversation;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.dao.entity.UserProfile;
import com.dust.wxclawbackfront.ai.document.DocumentGenerator;
import com.dust.wxclawbackfront.ai.ragflow.RagFlowClient;
import com.dust.wxclawbackfront.ai.service.AiConversationCrudService;
import com.dust.wxclawbackfront.ai.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import com.dust.wxclawbackfront.ilink.ILinkUserInputExtractor;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    // 异步保存消息的线程池
    private static final ExecutorService ASYNC_SAVE_EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "async-save");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final AiConversationCrudService crudService;
    private final CommandHandler commandHandler;
    private final ILinkUserInputExtractor userInputExtractor;
    private final ILinkMessageSender messageSender;
    private final ILinkRuntimeManager runtimeManager;
    private final DocumentGenerator documentGenerator;
    private final ObjectProvider<RagFlowClient> ragFlowClientProvider;
    private final AgentOrchestrator agentOrchestrator;
    private final UserMemoryService userMemoryService;

    // 消息防抖：记录最近处理的消息，避免重复调用 AI
    // key: userId + messageHash, value: 处理时间戳
    private final ConcurrentHashMap<String, Instant> recentMessageCache = new ConcurrentHashMap<>();
    private static final Duration DEBOUNCE_DURATION = Duration.ofSeconds(3);

    @Value("${wxclaw.ai.context.max-history-messages:12}")
    private int maxHistoryMessages;

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

        // 消息防抖：短时间内相同用户的相同消息只处理一次
        String userText = userInputExtractor.extractText(msg);
        if (userText != null && !userText.isBlank()) {
            String messageKey = userId + "::" + userText.trim().hashCode();
            Instant now = Instant.now();
            Instant lastProcessed = recentMessageCache.get(messageKey);
            if (lastProcessed != null && Duration.between(lastProcessed, now).compareTo(DEBOUNCE_DURATION) < 0) {
                log.debug("消息防抖：跳过重复消息 userId={}, text={}", userId, userText.trim().length() > 20 ? userText.trim().substring(0, 20) + "..." : userText.trim());
                return;
            }
            recentMessageCache.put(messageKey, now);
            // 定期清理过期缓存（避免内存泄漏）
            cleanExpiredCache(now);
        }

        // 设置用户上下文
        UserContextHolder.setUserId(userId);
        try {
            // 检查是否是新建对话指令
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

        log.info("收到用户消息: userId={}, sessionId={}, type={}", userId, sessionId, userInput.getMessageItemType());
        Instant start = Instant.now();
        ScheduledFuture<?> waitNoticeFuture = scheduleWaitNotice(userId);

        try {
            String reply = null;
            if ("IMAGE".equalsIgnoreCase(userInput.getMessageItemType())
                    && userInput.getError() == null
                    && userInput.getImageDescription() != null
                    && !userInput.getImageDescription().isBlank()) {
                reply = userInput.getImageDescription().trim();
            } else if ("IMAGE".equalsIgnoreCase(userInput.getMessageItemType())
                    && userInput.getError() != null
                    && !userInput.getError().isBlank()) {
                reply = "收到图片，但图片理解失败。请尝试重新发送图片或换一张更清晰的图片。\n错误信息：" + userInput.getError().trim();
            } else if ("FILE".equalsIgnoreCase(userInput.getMessageItemType())) {
                reply = handleFileUpload(userInput, userId);
                if (reply == null || reply.isBlank()) {
                    reply = "收到文件，但上传到知识库失败。请稍后再试。";
                }
            } else {
                reply = processWithAgent(userInput, historyMessages, userId, sessionId);
            }

            int responseTime = (int) Duration.between(start, Instant.now()).toMillis();

            if (reply != null && !reply.isBlank()) {
                if (documentGenerator.shouldGenerateDocument(reply)) {
                    String format = isMarkdownRequested(userInput.getDisplayText()) ? "markdown" : "txt";
                    DocumentGenerator.DocumentResult docResult = documentGenerator.generate(reply, format);
                    if (docResult.isSuccess()) {
                        messageSender.sendFile(userId, docResult.bytes(), docResult.fileName(), "内容较长，已生成文档，请查收。");
                    } else {
                        messageSender.sendText(userId, reply);
                    }
                } else {
                    messageSender.sendText(userId, reply);
                }
            }

            // 异步保存消息（reply 为 null 时保存占位符）
            final String finalReply = reply != null ? reply : "[MEDIA_SENT]";
            final int finalResponseTime = responseTime;
            CompletableFuture.runAsync(() -> {
                try {
                    crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, finalReply, null, finalResponseTime, null);
                } catch (Exception e) {
                    log.warn("异步保存消息失败: {}", e.getMessage());
                }
            }, ASYNC_SAVE_EXECUTOR);

        } catch (Exception ex) {
            handleError(ex, userId, sessionId, start);
        } finally {
            cancelWaitNotice(waitNoticeFuture);
        }
    }

    /**
     * 使用 Agent 编排器处理文本消息
     */
    private String processWithAgent(ILinkUserInput userInput, List<AiMessage> historyMessages,
                                     String userId, String sessionId) {
        log.info("开始 Agent 处理: userId={}, sessionId={}", userId, sessionId);

        // 构建 Agent 上下文
        List<UserProfile> profiles = userMemoryService.getProfiles(userId);

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .historyMessages(historyMessages)
                .userProfiles(profiles)
                .userText(userInput.getDisplayText())
                .build();

        // 调用 Agent 编排器
        AgentResult result = agentOrchestrator.orchestrate(userInput.getPromptText(), context);

        // 处理 Agent 结果
        if (!result.isSuccess()) {
            log.warn("Agent 处理失败: userId={}, error={}", userId, result.getErrorMessage());
            throw new RuntimeException(result.getErrorMessage());
        }

        log.info("Agent 处理完成: userId={}, hasMedia={}, mediaType={}", userId, result.hasMedia(), result.getMediaType());

        // 如果有媒体数据（图片/音频），发送媒体后返回 null，避免 processMessage 再发文本
        if (result.hasMedia()) {
            String mediaType = result.getMediaType();
            if (mediaType != null && mediaType.startsWith("image/")) {
                try {
                    messageSender.sendImage(userId, result.getMediaBytes(), result.getMediaFileName(), result.getReplyText());
                    log.info("Agent 图片已发送: userId={}", userId);
                } catch (Exception ex) {
                    log.warn("发送图片失败，降级为文本回复: {}", ex.getMessage());
                    return result.getReplyText();
                }
                return null;
            } else if (mediaType != null && mediaType.startsWith("audio/")) {
                try {
                    messageSender.sendFile(userId, result.getMediaBytes(), result.getMediaFileName(), "已生成音频文件，请查收。");
                    log.info("Agent 音频已发送: userId={}", userId);
                } catch (Exception sendFileEx) {
                    log.warn("发送音频文件失败，降级为文本回复: {}", sendFileEx.getMessage());
                    return result.getReplyText();
                }
                return null;
            }
        }

        return result.getReplyText();
    }

    /**
     * 处理文件上传到知识库
     */
    private String handleFileUpload(ILinkUserInput userInput, String userId) {
        try {
            RagFlowClient ragFlowClient = ragFlowClientProvider.getIfAvailable();
            if (ragFlowClient == null) {
                log.warn("RagFlowClient 不可用，无法上传文件到知识库");
                return "收到文件，但知识库服务暂不可用。";
            }

            String fileName = userInput.getFileName();
            byte[] fileBytes = userInput.getFileBytes();
            
            if (fileName == null || fileName.isBlank()) {
                fileName = "unknown_file";
            }

            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("文件内容为空: fileName={}, userId={}", fileName, userId);
                return "收到文件，但文件内容为空，请重新发送。";
            }

            log.info("准备上传文件到知识库: fileName={}, size={}, userId={}", fileName, fileBytes.length, userId);
            
            // 上传文件到RagFlow
            RagFlowClient.UploadResult result = ragFlowClient.uploadDocument(fileBytes, fileName);
            
            if (result.success()) {
                log.info("文件上传成功: fileName={}, documentId={}, userId={}", fileName, result.documentId(), userId);
                return "收到文件：" + fileName + "，已成功上传到知识库。";
            } else {
                log.error("文件上传失败: fileName={}, error={}, userId={}", fileName, result.message(), userId);
                return "收到文件，但上传到知识库失败：" + result.message();
            }
            
        } catch (Exception ex) {
            log.error("处理文件上传失败: userId={}, error={}", userId, ex.getMessage(), ex);
            return "收到文件，但处理失败：" + ex.getMessage();
        }
    }

    private void handleError(Exception ex, String userId, String sessionId, Instant start) {
        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();
        crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, null, null, responseTime, ex.getMessage());

        log.error("处理消息失败: userId={}, sessionId={}, error={}", userId, sessionId, ex.getMessage(), ex);
        try {
            String msgToUser = buildErrorMessage(ex);
            messageSender.sendText(userId, msgToUser);
        } catch (Exception ignored) {
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

    /**
     * 清理过期的防抖缓存
     */
    private void cleanExpiredCache(Instant now) {
        // 每100次调用清理一次，避免频繁清理
        if (recentMessageCache.size() > 100) {
            recentMessageCache.entrySet().removeIf(entry ->
                    Duration.between(entry.getValue(), now).compareTo(DEBOUNCE_DURATION.multipliedBy(10)) > 0
            );
        }
    }

    /**
     * 判断用户是否请求 markdown 格式的文档
     */
    private boolean isMarkdownRequested(String userText) {
        if (userText == null) {
            return false;
        }
        String text = userText.toLowerCase();
        return text.contains("markdown") || text.contains("md格式") || text.contains("md文档")
                || text.contains("markdown格式") || text.contains("markdown文档")
                || text.contains("返回md") || text.contains("返回markdown");
    }
}
