package com.dust.wxclawbackfront.observability.llm;

public record InvocationTraceContext(
        String tenantId,
        String botId,
        String conversationId,
        String sessionId,
        String traceId
) {
}
