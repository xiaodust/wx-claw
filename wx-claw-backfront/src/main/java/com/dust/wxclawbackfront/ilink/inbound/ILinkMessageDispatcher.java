package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.agent.llm.CommandHandler;
import com.dust.wxclawbackfront.bot.ragflow.RagFlowClient;
import com.dust.wxclawbackfront.bot.service.AiConversationCrudService;
import com.dust.wxclawbackfront.bot.agent.tools.shared.FileUploadValidator;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.exception.WxClawException;
import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import com.dust.wxclawbackfront.ilink.ILinkUserInputExtractor;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.UUID;

/**
 * ILink 入站消息处理器（薄协调层）
 * 负责协调各个组件处理收到的用户消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageDispatcher {

    private static final int MESSAGE_TYPE_USER = 0;
    private static final int MESSAGE_TYPE_ASSISTANT = 1;
    private static final String MEDIA_SENT_PLACEHOLDER = "[MEDIA_SENT]";

    // 核心依赖
    private final AiConversationCrudService crudService;
    private final CommandHandler commandHandler;
    private final ILinkUserInputExtractor userInputExtractor;
    private final ILinkMessageSender messageSender;
    private final ILinkRuntimeManager runtimeManager;
    private final ObjectProvider<RagFlowClient> ragFlowClientProvider;
    @Qualifier("asyncSaveExecutor")
    private final ExecutorService asyncSaveExecutor;

    // 拆分出的组件
    private final MessageDebouncer messageDebouncer;
    private final MediaContextManager mediaContextManager;
    private final AgentResponseProcessor agentResponseProcessor;
    private final DocumentReplyHandler documentReplyHandler;
    private final WaitNoticeService waitNoticeService;
    private final ErrorHandler errorHandler;
    private final FileUploadValidator fileUploadValidator;
    private final ILinkMessageReceiptStore messageReceiptStore;

    @Value("${wxclaw.ai.context.max-history-messages:12}")
    private int maxHistoryMessages;

    /**
     * 处理入站消息（主入口）
     */
    public void dispatch(BotRuntimeKey runtimeKey, WeixinMessage msg) {
        if (!claim(runtimeKey, msg)) {
            return;
        }
        dispatchClaimed(runtimeKey, msg);
    }

    public boolean claim(BotRuntimeKey runtimeKey, WeixinMessage msg) {
        if (msg == null) {
            return false;
        }
        String userId = msg.getFrom_user_id();
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (!messageReceiptStore.claim(runtimeKey, msg)) {
            log.info("忽略已处理的 iLink 重放消息: tenantId={}, botId={}, messageId={}, userId={}",
                    runtimeKey.tenantId(), runtimeKey.botId(), msg.getMessage_id(), userId);
            return false;
        }
        return true;
    }

    public void dispatchClaimed(BotRuntimeKey runtimeKey, WeixinMessage msg) {
        String userId = msg.getFrom_user_id();
        String contextToken = msg.getContext_token();
        // 消息防抖
        String userText = userInputExtractor.extractText(msg);
        if (!messageDebouncer.shouldProcess(userId, userText)) {
            return;
        }

        // 设置用户上下文
        TenantContextHolder.set(TenantContext.ilink(
                runtimeKey.tenantId(), runtimeKey.botId(), userId, UUID.randomUUID().toString()));
        try {
            // 检查是否是新建对话指令
            if (isNewConversationIntent(userText)) {
                handleNewConversation(userId);
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
            TenantContext tenantContext = TenantContextHolder.require();
            InvocationTraceContextHolder.set(new InvocationTraceContext(
                    runtimeKey.tenantId(), runtimeKey.botId(), activeConversation.getId(), sessionId,
                    tenantContext.requestId()));

            processMessage(runtimeKey, msg, userId, contextToken, sessionId);
        } finally {
            InvocationTraceContextHolder.clear();
            TenantContextHolder.clear();
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

    private void handleNewConversation(String userId) {
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

    private void processMessage(BotRuntimeKey runtimeKey, WeixinMessage msg, String userId, String contextToken, String sessionId) {
        ILinkClient client = runtimeManager.requireClient(runtimeKey);
        ILinkUserInput userInput = userInputExtractor.extract(client, msg);
        if (userInput == null) {
            String trimmed = userInputExtractor.extractText(msg);
            if (trimmed == null || trimmed.isBlank()) {
                return;
            }
            userInput = ILinkUserInput.text(trimmed.trim());
        }

        // 使用分页查询最近的消息，避免内存溢出
        List<AiMessage> historyMessages = crudService.listRecentMessages(sessionId, maxHistoryMessages);

        crudService.createOrGetConversation(sessionId, userId);
        crudService.appendMessage(sessionId, MESSAGE_TYPE_USER, userInput.getPersistText(), null, null, null);

        log.info("收到用户消息: userId={}, sessionId={}, type={}", userId, sessionId, userInput.getMessageItemType());
        Instant start = Instant.now();
        ScheduledFuture<?> waitNoticeFuture = waitNoticeService.schedule(userId);

        try {
            String reply = processUserInput(userInput, userId, sessionId);

            // 发送回复
            documentReplyHandler.sendReply(userId, reply, userInput.getDisplayText());

            // 异步保存消息
            asyncSaveMessage(sessionId, reply, start);

        } catch (Exception ex) {
            errorHandler.handle(ex, userId, sessionId, start);
        } finally {
            waitNoticeService.cancel(waitNoticeFuture);
        }
    }

    /**
     * 处理用户输入，返回回复文本
     */
    private String processUserInput(ILinkUserInput userInput, String userId, String sessionId) {
        String messageType = userInput.getMessageItemType();

        // 处理图片消息
        if ("IMAGE".equalsIgnoreCase(messageType)) {
            return handleImageMessage(userInput, userId, sessionId);
        }

        // 处理视频消息
        if ("VIDEO".equalsIgnoreCase(messageType)) {
            return handleVideoMessage(userInput, userId, sessionId);
        }

        // 处理文件消息
        if ("FILE".equalsIgnoreCase(messageType)) {
            return handleFileMessage(userInput, userId);
        }

        // 处理文本消息（可能包含待处理的媒体上下文）
        return handleTextMessage(userInput, userId, sessionId);
    }

    private String handleImageMessage(ILinkUserInput userInput, String userId, String sessionId) {
        if (userInput.getError() != null && !userInput.getError().isBlank()) {
            log.warn("图片理解失败，提供降级处理: userId={}, error={}", userId, userInput.getError());
            // 降级：存储图片上下文，让用户手动描述
            mediaContextManager.storeImageContext(userId, "[图片理解失败，请描述图片内容]");
            return "收到图片，但自动理解失败。请简单描述图片内容或告诉我你想让我做什么。";
        }

        if (userInput.getImageDescription() != null && !userInput.getImageDescription().isBlank()) {
            mediaContextManager.storeImageContext(userId, userInput.getImageDescription().trim());
            return "收到图片，请告诉我你想让我对这张图片做什么？\n例如：描述图片内容、提取文字、分析图片、根据图片回答问题等。";
        }

        return null;
    }

    private String handleVideoMessage(ILinkUserInput userInput, String userId, String sessionId) {
        if (userInput.getError() != null && !userInput.getError().isBlank()) {
            return "收到视频，但视频理解失败。请尝试重新发送。\n错误信息：" + userInput.getError().trim();
        }

        if (userInput.getVideoDescription() != null && !userInput.getVideoDescription().isBlank()) {
            mediaContextManager.storeVideoContext(userId, userInput.getVideoDescription().trim());
            return "收到视频，请告诉我你想让我对这个视频做什么？\n例如：描述视频内容、分析视频、根据视频回答问题等。";
        }

        return null;
    }

    private String handleFileMessage(ILinkUserInput userInput, String userId) {
        mediaContextManager.storeFileContext(userId, userInput);
        String fileName = userInput.getFileName() != null ? userInput.getFileName() : "未知文件";
        return "收到文件：" + fileName + "，请告诉我你想让我做什么？\n例如：上传到知识库、分析文件内容、总结要点、优化内容、回答关于文件的问题等。";
    }

    private String handleTextMessage(ILinkUserInput userInput, String userId, String sessionId) {
        // 检查是否有待处理的图片上下文
        String pendingImageDesc = mediaContextManager.takeImageContext(userId);
        if (pendingImageDesc != null && !pendingImageDesc.isBlank()) {
            String combinedText = "用户发送了一张图片，图片内容描述如下：\n" + pendingImageDesc
                    + "\n\n用户的要求：" + userInput.getDisplayText();
            return agentResponseProcessor.process(ILinkUserInput.text(combinedText), Collections.emptyList(), userId, sessionId);
        }

        // 检查是否有待处理的视频上下文
        String pendingVideoDesc = mediaContextManager.takeVideoContext(userId);
        if (pendingVideoDesc != null && !pendingVideoDesc.isBlank()) {
            String combinedText = "用户发送了一个视频，视频内容描述如下：\n" + pendingVideoDesc
                    + "\n\n用户的要求：" + userInput.getDisplayText();
            return agentResponseProcessor.process(ILinkUserInput.text(combinedText), Collections.emptyList(), userId, sessionId);
        }

        // 检查是否有待处理的文件上下文
        String pendingFileInfo = mediaContextManager.takeFileContext(userId);
        if (pendingFileInfo != null && !pendingFileInfo.isBlank()) {
            String userIntent = userInput.getDisplayText();

            // 检查用户是否要求上传到知识库
            if (mediaContextManager.isKnowledgeBaseUploadIntent(userIntent)) {
                MediaContextManager.PendingFileUpload pendingFile = mediaContextManager.takePendingFileUpload(userId);
                if (pendingFile != null) {
                    return handleFileUploadDirect(pendingFile.fileName(), pendingFile.fileBytes(), userId);
                } else {
                    return "文件数据已过期，请重新发送文件。";
                }
            } else {
                // 其他意图：组合文件信息 + 用户需求，交给 Agent 处理
                mediaContextManager.takePendingFileUpload(userId); // 清理
                String combinedText = pendingFileInfo + "\n\n用户的要求：" + userIntent;
                return agentResponseProcessor.process(ILinkUserInput.text(combinedText), Collections.emptyList(), userId, sessionId);
            }
        }

        // 普通文本消息，交给 Agent 处理
        List<AiMessage> historyMessages = crudService.listRecentMessages(sessionId, maxHistoryMessages);
        return agentResponseProcessor.process(userInput, historyMessages, userId, sessionId);
    }

    /**
     * 直接上传文件到知识库
     */
    private String handleFileUploadDirect(String fileName, byte[] fileBytes, String userId) {
        try {
            // 文件验证
            FileUploadValidator.ValidationResult validation = fileUploadValidator.validate(fileName, fileBytes);
            if (!validation.isValid()) {
                log.warn("文件验证失败: fileName={}, userId={}, error={}", fileName, userId, validation.getError());
                return validation.getError();
            }

            RagFlowClient ragFlowClient = ragFlowClientProvider.getIfAvailable();
            if (ragFlowClient == null) {
                log.warn("RagFlowClient 不可用，无法上传文件到知识库");
                return "知识库服务暂不可用，请稍后再试。";
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = "unknown_file";
            }

            log.info("上传文件到知识库: fileName={}, size={}, userId={}", fileName, fileBytes.length, userId);
            RagFlowClient.UploadResult result = ragFlowClient.uploadDocument(fileBytes, fileName);

            if (result.success()) {
                log.info("文件上传成功: fileName={}, documentId={}, userId={}", fileName, result.documentId(), userId);
                return "文件「" + fileName + "」已成功上传到知识库。";
            } else {
                log.error("文件上传失败: fileName={}, error={}, userId={}", fileName, result.message(), userId);
                return "上传到知识库失败：" + result.message();
            }
        } catch (Exception ex) {
            log.error("上传文件到知识库失败: userId={}, error={}", userId, ex.getMessage(), ex);
            return "上传失败：" + ex.getMessage();
        }
    }

    /**
     * 异步保存消息
     */
    private void asyncSaveMessage(String sessionId, String reply, Instant start) {
        int responseTime = (int) (System.currentTimeMillis() - start.toEpochMilli());
        final String finalReply = reply != null ? reply : MEDIA_SENT_PLACEHOLDER;

        CompletableFuture.runAsync(() -> {
            try {
                crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, finalReply, null, responseTime, null);
            } catch (Exception e) {
                log.warn("异步保存消息失败: {}", e.getMessage());
            }
        }, asyncSaveExecutor);
    }
}
