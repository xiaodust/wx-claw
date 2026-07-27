package com.dust.wxclawbackfront.observability.llm.service;

import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import com.dust.wxclawbackfront.observability.llm.entity.LlmInvocation;
import com.dust.wxclawbackfront.observability.llm.repository.LlmInvocationRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LlmInvocationRecorder {
    private final LlmInvocationRepository repository;
    private final LlmPayloadSanitizer sanitizer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InvocationHandle start(String invocationType, String provider, String model, String requestPayload) {
        TenantContext tenant = TenantContextHolder.require();
        InvocationTraceContext trace = InvocationTraceContextHolder.getNullable();
        String traceId = trace != null && trace.traceId() != null ? trace.traceId() : tenant.requestId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        LlmPayloadSanitizer.SanitizedPayload request = sanitizer.sanitize(requestPayload);
        LlmInvocation invocation = new LlmInvocation();
        invocation.setBotId(trace != null ? trace.botId() : tenant.botId());
        invocation.setConversationId(trace != null ? trace.conversationId() : null);
        invocation.setSessionId(trace != null ? trace.sessionId() : null);
        invocation.setTraceId(traceId);
        invocation.setSequenceNo(repository.findMaxSequence(tenant.tenantId(), traceId) + 1);
        invocation.setInvocationType(invocationType);
        invocation.setProvider(provider);
        invocation.setModel(model);
        invocation.setStatus("RUNNING");
        invocation.setRequestPayload(request.value());
        invocation.setRequestTruncated(request.truncated());
        invocation.setRequestOriginalLength(request.originalLength());
        invocation.setRequestSha256(request.sha256());
        invocation.setStartedAt(LocalDateTime.now());
        LlmInvocation saved = repository.save(invocation);
        return new InvocationHandle(saved.getId(), saved.getStartedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(InvocationHandle handle, String responsePayload, String toolCallsJson,
                        Integer inputTokens, Integer outputTokens) {
        LlmInvocation invocation = requireInvocation(handle.id());
        LlmPayloadSanitizer.SanitizedPayload response = sanitizer.sanitize(responsePayload);
        invocation.setStatus("SUCCESS");
        invocation.setResponsePayload(response.value());
        invocation.setResponseTruncated(response.truncated());
        invocation.setResponseOriginalLength(response.originalLength());
        invocation.setResponseSha256(response.sha256());
        invocation.setToolCallsJson(sanitizer.sanitize(toolCallsJson).value());
        invocation.setInputTokens(inputTokens);
        invocation.setOutputTokens(outputTokens);
        complete(invocation, handle.startedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(InvocationHandle handle, Throwable error) {
        failure(handle, error, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(InvocationHandle handle, Throwable error, String responsePayload) {
        LlmInvocation invocation = requireInvocation(handle.id());
        LlmPayloadSanitizer.SanitizedPayload response = sanitizer.sanitize(responsePayload);
        invocation.setStatus("FAILED");
        invocation.setResponsePayload(response.value());
        invocation.setResponseTruncated(response.truncated());
        invocation.setResponseOriginalLength(response.originalLength());
        invocation.setResponseSha256(response.sha256());
        invocation.setErrorType(error == null ? null : error.getClass().getName());
        invocation.setErrorMessage(error == null ? null : sanitizer.sanitize(error.getMessage()).value());
        complete(invocation, handle.startedAt());
    }

    private LlmInvocation requireInvocation(String id) {
        String tenantId = TenantContextHolder.require().tenantId();
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalStateException("LLM invocation not found: " + id));
    }

    private void complete(LlmInvocation invocation, LocalDateTime startedAt) {
        LocalDateTime completedAt = LocalDateTime.now();
        invocation.setCompletedAt(completedAt);
        long duration = Duration.between(startedAt, completedAt).toMillis();
        invocation.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, duration)));
        repository.save(invocation);
    }

    public record InvocationHandle(String id, LocalDateTime startedAt) {
    }
}
