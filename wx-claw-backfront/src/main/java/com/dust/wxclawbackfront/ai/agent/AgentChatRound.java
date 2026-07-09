package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;

import java.util.List;

public record AgentChatRound(int round,
                             String type,
                             String prompt,
                             String reply,
                             List<AiToolInvocationStore.Invocation> tools,
                             Object reason) {
}
