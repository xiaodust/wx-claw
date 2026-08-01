package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpException;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore.PendingResume;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerUserKey;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.dust.wxclawbackfront.bot.service.AiConversationCrudService;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerTaskService {
    private static final int MAX_JD_LENGTH = 50_000;

    private final JobHelperMcpClient client;
    private final CareerResumeContextStore resumeStore;
    private final CareerReplyFormatter formatter;
    private final ILinkMessageSender messageSender;
    private final AiConversationCrudService conversationService;
    private final ExecutorService executor;
    private final ConcurrentMap<TaskKey, String> inFlight = new ConcurrentHashMap<>();

    public CareerTaskService(JobHelperMcpClient client,
                             CareerResumeContextStore resumeStore,
                             CareerReplyFormatter formatter,
                             ILinkMessageSender messageSender,
                             AiConversationCrudService conversationService,
                             @Qualifier("careerTaskExecutor") ExecutorService executor) {
        this.client = client;
        this.resumeStore = resumeStore;
        this.formatter = formatter;
        this.messageSender = messageSender;
        this.conversationService = conversationService;
        this.executor = executor;
    }

    public TaskSubmission submitScore(String jobDescription) {
        if (jobDescription != null && jobDescription.length() > MAX_JD_LENGTH) {
            return TaskSubmission.rejected("岗位 JD 不能超过 50000 字");
        }
        PendingResume resume = resumeStore.getCurrent().orElse(null);
        if (resume == null) {
            return TaskSubmission.rejected("请先发送 PDF 简历，再进行简历评分。");
        }
        String normalizedJd = jobDescription == null ? null : jobDescription.trim();
        String parameterHash = hash(normalizedJd == null ? "" : normalizedJd);
        return submit("简历评分", resume, parameterHash,
                () -> formatter.formatScoreMessages(client.score(
                        CareerUserKey.current().jobHelperIdentity(), normalizedJd)));
    }

    public TaskSubmission submitRecommendation(JobHelperDtos.JobFilters filters) {
        PendingResume resume = resumeStore.getCurrent().orElse(null);
        if (resume == null) {
            return TaskSubmission.rejected("请先发送 PDF 简历，再进行个性化岗位推荐。");
        }
        String parameterHash = hash(String.valueOf(filters));
        return submit("岗位推荐", resume, parameterHash, () -> {
            JobHelperDtos.JobRecommendationResponse response = client.recommend(
                    CareerUserKey.current().jobHelperIdentity(), filters);
            if (response.recommendations().isEmpty()) {
                return List.of("暂未找到符合条件的推荐岗位，可以尝试放宽城市、关键词或匹配分要求。\n请求编号："
                        + response.requestId());
            }
            return formatter.formatRecommendations(response);
        });
    }

    private TaskSubmission submit(String operation, PendingResume resume, String parameterHash, TaskAction action) {
        CareerUserKey userKey = CareerUserKey.current();
        TaskKey key = new TaskKey(userKey, operation, resume.sha256(), parameterHash);
        String taskId = UUID.randomUUID().toString();
        String existingTaskId = inFlight.putIfAbsent(key, taskId);
        if (existingTaskId != null) {
            return TaskSubmission.accepted(existingTaskId, true,
                    "相同任务正在处理中，无需重复提交。任务编号：" + existingTaskId);
        }
        try {
            TenantContext context = TenantContextHolder.require();
            String recipientId = context.channelUserId() == null || context.channelUserId().isBlank()
                    ? context.internalUserId() : context.channelUserId();
            InvocationTraceContext traceContext = InvocationTraceContextHolder.getNullable();
            String sessionId = traceContext == null ? null : traceContext.sessionId();
            CompletableFuture.runAsync(
                    () -> execute(key, taskId, operation, recipientId, sessionId, action), executor);
            return TaskSubmission.accepted(taskId, false,
                    operation + "任务已开始，完成后会通过微信发送结果。任务编号：" + taskId);
        } catch (RejectedExecutionException exception) {
            inFlight.remove(key, taskId);
            return TaskSubmission.rejected("当前职业任务较多，请稍后再试。");
        }
    }

    private void execute(TaskKey key, String taskId, String operation, String userId,
                         String sessionId, TaskAction action) {
        try {
            List<String> messages = action.execute();
            for (String message : messages) {
                saveToConversation(sessionId, message);
                sendChunks(userId, message);
            }
            log.info("职业任务完成: taskId={}, operation={}, tenantId={}, botId={}, messageCount={}",
                    taskId, operation, key.userKey().tenantId(), key.userKey().botId(), messages.size());
        } catch (JobHelperMcpException exception) {
            sendFailure(userId, sessionId, formatter.formatFailure(operation, exception.getCode(),
                    exception.getMessage(), exception.getRequestId()), taskId, exception);
        } catch (Exception exception) {
            sendFailure(userId, sessionId, formatter.formatFailure(operation, "INTERNAL_ERROR",
                    "处理过程中发生异常，请稍后再试。", null), taskId, exception);
        } finally {
            inFlight.remove(key, taskId);
        }
    }

    private void saveToConversation(String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            conversationService.appendMessage(sessionId, 1, message, null, null, null);
        } catch (Exception exception) {
            log.warn("职业任务结果写入对话历史失败: sessionId={}, error={}", sessionId, exception.getMessage());
        }
    }

    private void sendFailure(String userId, String sessionId, String message, String taskId, Exception exception) {
        log.warn("职业任务失败: taskId={}, error={}", taskId, exception.getMessage(), exception);
        try {
            String failureMessage = message + "\n任务编号：" + taskId;
            saveToConversation(sessionId, failureMessage);
            messageSender.sendText(userId, failureMessage);
        } catch (Exception sendException) {
            log.error("职业任务失败通知发送失败: taskId={}, error={}", taskId, sendException.getMessage());
        }
    }

    private void sendChunks(String userId, String text) throws Exception {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 1800, text.length());
            if (end < text.length()) {
                int lineBreak = text.lastIndexOf('\n', end);
                if (lineBreak > start) {
                    end = lineBreak + 1;
                }
            }
            messageSender.sendText(userId, text.substring(start, end).trim());
            start = end;
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record TaskKey(CareerUserKey userKey, String operation, String resumeHash, String parameterHash) {
    }

    @FunctionalInterface
    private interface TaskAction {
        List<String> execute();
    }

    public record TaskSubmission(boolean accepted, String taskId, boolean duplicate, String message) {
        static TaskSubmission accepted(String taskId, boolean duplicate, String message) {
            return new TaskSubmission(true, taskId, duplicate, message);
        }

        static TaskSubmission rejected(String message) {
            return new TaskSubmission(false, null, false, message);
        }
    }
}
