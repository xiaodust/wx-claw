package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.agent.AgentChatResult;
import com.dust.wxclawbackfront.ai.agent.ToolPollingAgent;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 聊天追踪数据组装器
 * 负责回填 accumulator 的 trace 相关字段
 */
@Component
@RequiredArgsConstructor
public class ChatTraceAssembler {

    private final ToolPollingAgent toolPollingAgent;

    /**
     * 将 agent 执行结果回填到 accumulator
     * @param accumulator 内容累加器
     * @param result agent 执行结果
     */
    public void assembleTrace(AIContentAccumulator accumulator, AgentChatResult result) {
        if (accumulator == null || result == null) {
            return;
        }

        accumulator.setFinalContent(result.content());

        if (result.invocations() != null && !result.invocations().isEmpty()) {
            accumulator.setToolName(toolPollingAgent.joinToolNames(result.invocations()));
            accumulator.setToolRequest(toolPollingAgent.toJsonSafely(result.invocations().stream()
                    .map(AiToolInvocationStore.Invocation::toolRequest)
                    .toList()));
            accumulator.setToolResponse(toolPollingAgent.toJsonSafely(result.invocations().stream()
                    .map(AiToolInvocationStore.Invocation::toolResponse)
                    .toList()));
        }

        accumulator.setAgentRounds(result.rounds() == null ? 0 : result.rounds().size());
        accumulator.setAgentCompleted(result.completed());
        accumulator.setAgentTraceJson(toolPollingAgent.toJsonSafely(result.rounds()));
    }

    /**
     * 设置基础请求信息
     * @param accumulator 内容累加器
     * @param requestText 请求文本
     * @param model 模型名称
     * @param llmRequestJson LLM 请求 JSON
     */
    public void setBasicInfo(AIContentAccumulator accumulator, String requestText, String model, String llmRequestJson) {
        if (accumulator == null) {
            return;
        }
        accumulator.setRequestText(requestText);
        accumulator.setModel(model);
        accumulator.setLlmRequestJson(llmRequestJson);
    }
}
