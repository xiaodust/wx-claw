package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;

import java.util.List;

public record AgentChatResult(String content,
                              List<AiToolInvocationStore.Invocation> invocations,
                              List<AgentChatRound> rounds,
                              boolean completed) {
}
