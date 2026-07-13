package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具调用 Agent
 * 由 LLM 自主判断并调用工具（Spring AI 自动处理 function calling 循环）
 * 通过 maxRounds 控制最大工具调用轮数，防止 LLM 陷入无限调用循环
 */
@Slf4j
@Component
public class ToolPollingAgent {

    private final int maxRounds;

    public ToolPollingAgent(@Value("${wxclaw.ai.chat.max-rounds:3}") int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public AgentChatResult run(String userMessage, String firstPrompt, AgentLlmCaller llmCaller) {
        List<AiToolInvocationStore.Invocation> allInvocations = new ArrayList<>();
        List<AgentChatRound> rounds = new ArrayList<>();

        // Spring AI ChatClient.call() 内部会自动处理 LLM 的多轮 function calling 循环
        // （LLM 请求工具 → 框架执行 → 结果回传 → LLM 决定是否继续调用工具）
        // maxRounds 作为安全上限，防止 LLM 陷入无限工具调用循环
        AgentLlmCaller.LlmCallResult result = llmCaller.call(firstPrompt);
        String finalContent = result.content();
        allInvocations.addAll(result.invocations());
        rounds.add(new AgentChatRound(1, "llm", firstPrompt, result.content(), result.invocations(), null));

        // 安全检查：如果 LLM 在一轮内调用工具次数超过 maxRounds，记录警告并截断
        if (!allInvocations.isEmpty() && allInvocations.size() > maxRounds) {
            log.warn("工具调用轮数 {} 超过上限 {}，已截断。调用的工具: {}",
                    allInvocations.size(), maxRounds, joinToolNames(allInvocations));
            List<AiToolInvocationStore.Invocation> truncated = allInvocations.subList(0, maxRounds);
            finalContent = finalContent + "\n\n[工具调用已达上限(" + maxRounds + "轮)，已调用工具: " + joinToolNames(truncated) + "]";
            allInvocations = new ArrayList<>(truncated);
        }

        return new AgentChatResult(finalContent, allInvocations, rounds, true);
    }

    public String joinToolNames(List<AiToolInvocationStore.Invocation> invocations) {
        if (invocations == null || invocations.isEmpty()) {
            return null;
        }
        return invocations.stream()
                .map(AiToolInvocationStore.Invocation::toolName)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining(","));
    }
}
