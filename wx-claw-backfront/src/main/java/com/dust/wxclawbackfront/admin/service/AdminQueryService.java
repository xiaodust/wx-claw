package com.dust.wxclawbackfront.admin.service;

import com.dust.wxclawbackfront.admin.api.dto.AdminDtos;
import com.dust.wxclawbackfront.admin.security.AdminAccessGuard;
import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeSnapshot;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatus;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import com.dust.wxclawbackfront.observability.llm.entity.LlmInvocation;
import com.dust.wxclawbackfront.observability.llm.repository.LlmInvocationRepository;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQueryService {
    private final AdminAccessGuard accessGuard;
    private final TenantBotRepository botRepository;
    private final BotRuntimeStatusRegistry statusRegistry;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final LlmInvocationRepository invocationRepository;

    public AdminDtos.Overview overview(String requestedTenantId) {
        String tenantId = accessGuard.resolveTenant(requestedTenantId);
        List<AdminDtos.BotStatus> bots = bots(tenantId, null, null);
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long conversations = tenantId == null
                ? conversationRepository.countByCreatedTimeGreaterThanEqual(today)
                : conversationRepository.countByTenantIdAndCreatedTimeGreaterThanEqual(tenantId, today);
        long messages = tenantId == null
                ? messageRepository.countByCreateTimeGreaterThanEqual(today)
                : messageRepository.countByTenantIdAndCreateTimeGreaterThanEqual(tenantId, today);
        long invocations = tenantId == null
                ? invocationRepository.countByStartedAtGreaterThanEqual(today)
                : invocationRepository.countByTenantIdAndStartedAtGreaterThanEqual(tenantId, today);
        long failed = tenantId == null
                ? invocationRepository.countByStatusAndStartedAtGreaterThanEqual("FAILED", today)
                : invocationRepository.countByTenantIdAndStatusAndStartedAtGreaterThanEqual(tenantId, "FAILED", today);
        return new AdminDtos.Overview(bots.size(), countStatus(bots, "ONLINE"),
                countStatus(bots, "WAITING_QR"), countStatus(bots, "ERROR"),
                conversations, messages, invocations, failed, Instant.now());
    }

    public List<AdminDtos.BotStatus> bots(String requestedTenantId, String runtimeStatus, String keyword) {
        String tenantId = accessGuard.resolveTenant(requestedTenantId);
        List<TenantBot> configured = tenantId == null ? botRepository.findAll() : botRepository.findAllByTenantId(tenantId);
        String normalizedStatus = normalize(runtimeStatus);
        String normalizedKeyword = normalize(keyword);
        return configured.stream().map(this::toBotStatus)
                .filter(bot -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(bot.runtimeStatus()))
                .filter(bot -> normalizedKeyword == null
                        || contains(bot.botId(), normalizedKeyword) || contains(bot.displayName(), normalizedKeyword))
                .sorted(Comparator.comparing(AdminDtos.BotStatus::tenantId).thenComparing(AdminDtos.BotStatus::botId))
                .toList();
    }

    public AdminDtos.BotStatus bot(String tenantId, String botId) {
        String resolvedTenant = accessGuard.resolveTenant(tenantId);
        if (resolvedTenant == null) {
            resolvedTenant = tenantId;
        }
        String finalTenant = resolvedTenant;
        return botRepository.findAllByTenantId(finalTenant).stream()
                .filter(bot -> bot.getBotId().equals(botId)).findFirst().map(this::toBotStatus)
                .orElseThrow(() -> new IllegalArgumentException("Bot not found"));
    }

    public Page<AdminDtos.Conversation> conversations(String requestedTenantId, String botId,
                                                       String internalUserId, String sessionId,
                                                       String keyword, Boolean active,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       int page, int size) {
        String tenantId = accessGuard.resolveTenant(requestedTenantId);
        Specification<AiConversation> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (normalize(botId) != null) predicates.add(cb.equal(root.get("botId"), botId.trim()));
            if (normalize(internalUserId) != null) predicates.add(cb.equal(root.get("internalUserId"), internalUserId.trim()));
            if (normalize(sessionId) != null) predicates.add(cb.equal(root.get("sessionId"), sessionId.trim()));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (startTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdTime"), startTime));
            if (endTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdTime"), endTime));
            String search = normalize(keyword);
            if (search != null) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("sessionId")), like),
                        cb.like(cb.lower(root.get("internalUserId")), like),
                        cb.like(cb.lower(root.get("username")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return conversationRepository.findAll(specification, PageRequest.of(Math.max(0, page), clampSize(size),
                        Sort.by(Sort.Direction.DESC, "updatedTime"))).map(this::toConversation);
    }

    public AdminDtos.Conversation conversation(String id) {
        AiConversation conversation = requireConversation(id);
        return toConversation(conversation);
    }

    public List<AdminDtos.Message> messages(String conversationId) {
        AiConversation conversation = requireConversation(conversationId);
        return messageRepository.findAllByTenantIdAndConversationIdOrderByMessageSeqAsc(
                conversation.getTenantId(), conversationId).stream().map(this::toMessage).toList();
    }

    public List<AdminDtos.InvocationSummary> invocations(String conversationId) {
        AiConversation conversation = requireConversation(conversationId);
        return invocationRepository.findAllByTenantIdAndConversationIdOrderByStartedAtAscSequenceNoAsc(
                conversation.getTenantId(), conversationId).stream().map(this::toInvocationSummary).toList();
    }

    public AdminDtos.InvocationDetail invocation(String invocationId) {
        LlmInvocation invocation = invocationRepository.findById(invocationId)
                .orElseThrow(() -> new IllegalArgumentException("Invocation not found"));
        accessGuard.ensureTenant(invocation.getTenantId());
        return toInvocationDetail(invocation);
    }

    private AiConversation requireConversation(String id) {
        AiConversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        accessGuard.ensureTenant(conversation.getTenantId());
        return conversation;
    }

    private AdminDtos.BotStatus toBotStatus(TenantBot bot) {
        BotRuntimeSnapshot snapshot = statusRegistry.get(new BotRuntimeKey(bot.getTenantId(), bot.getBotId()))
                .orElse(new BotRuntimeSnapshot(new BotRuntimeKey(bot.getTenantId(), bot.getBotId()),
                        BotRuntimeStatus.OFFLINE, null, null, null, null, null, null, 0, false));
        return new AdminDtos.BotStatus(bot.getTenantId(), bot.getBotId(), bot.getDisplayName(), bot.getChannel(),
                bot.getStatus(), snapshot.status().name(), snapshot.connectedAt(), snapshot.statusChangedAt(),
                snapshot.lastPollAt(), snapshot.lastMessageAt(), snapshot.lastErrorAt(), snapshot.lastError(),
                snapshot.reconnectAttempts(), snapshot.resumeContextAvailable());
    }

    private AdminDtos.Conversation toConversation(AiConversation entity) {
        return new AdminDtos.Conversation(entity.getId(), entity.getTenantId(), entity.getBotId(),
                entity.getSessionId(), entity.getUsername(), entity.getInternalUserId(), entity.getChannel(),
                Boolean.TRUE.equals(entity.getActive()), entity.getMessageCount(), entity.getLastMessageTime(),
                entity.getCreatedTime(), entity.getUpdatedTime());
    }

    private AdminDtos.Message toMessage(AiMessage entity) {
        return new AdminDtos.Message(entity.getId(), entity.getTenantId(), entity.getConversationId(),
                entity.getSessionId(), entity.getMessageType(), entity.getContent(), entity.getReasoningContent(),
                entity.getMessageSeq(), entity.getResponseTime(), entity.getErrorMsg(), entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private AdminDtos.InvocationSummary toInvocationSummary(LlmInvocation entity) {
        return new AdminDtos.InvocationSummary(entity.getId(), entity.getTenantId(), entity.getBotId(),
                entity.getConversationId(), entity.getSessionId(), entity.getTraceId(), entity.getSequenceNo(),
                entity.getInvocationType(), entity.getProvider(), entity.getModel(), entity.getStatus(),
                entity.getInputTokens(), entity.getOutputTokens(), entity.getDurationMs(), entity.getErrorMessage(),
                entity.getStartedAt(), entity.getCompletedAt());
    }

    private AdminDtos.InvocationDetail toInvocationDetail(LlmInvocation entity) {
        return new AdminDtos.InvocationDetail(entity.getId(), entity.getTenantId(), entity.getBotId(),
                entity.getConversationId(), entity.getSessionId(), entity.getTraceId(), entity.getParentInvocationId(),
                entity.getSequenceNo(), entity.getInvocationType(), entity.getProvider(), entity.getModel(),
                entity.getStatus(), entity.getRequestPayload(), entity.getResponsePayload(), entity.getToolCallsJson(),
                Boolean.TRUE.equals(entity.getRequestTruncated()), Boolean.TRUE.equals(entity.getResponseTruncated()),
                entity.getRequestOriginalLength(), entity.getResponseOriginalLength(), entity.getRequestSha256(),
                entity.getResponseSha256(), entity.getInputTokens(), entity.getOutputTokens(), entity.getDurationMs(),
                entity.getErrorType(), entity.getErrorMessage(), entity.getStartedAt(), entity.getCompletedAt());
    }

    private long countStatus(List<AdminDtos.BotStatus> bots, String status) {
        return bots.stream().filter(bot -> status.equals(bot.runtimeStatus())).count();
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(100, size));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }
}
