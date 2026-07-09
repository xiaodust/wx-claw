package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;

import java.util.List;

@FunctionalInterface
public interface AgentLlmCaller {
    LlmCallResult call(String prompt);

    record LlmCallResult(String content, List<AiToolInvocationStore.Invocation> invocations) {
    }
}
