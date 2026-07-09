package com.dust.wxclawbackfront.ai.agent;

import com.dust.wxclawbackfront.ai.agent.detector.ToolRequirementDetector;
import com.dust.wxclawbackfront.ai.agent.detector.WebSearchToolRequirementDetector;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具轮询 Agent
 * 只负责编排，具体规则委托给各 detector
 */
@Slf4j
@Component
public class ToolPollingAgent {

    private final List<ToolRequirementDetector> detectors;
    private final WebSearchToolRequirementDetector webSearchDetector;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRounds;
    private final boolean directToolFill;

    public ToolPollingAgent(List<ToolRequirementDetector> detectors,
                            WebSearchToolRequirementDetector webSearchDetector,
                            ObjectMapper objectMapper,
                            @Value("${wxclaw.ai.agent.enabled:true}") boolean enabled,
                            @Value("${wxclaw.ai.agent.max-rounds:2}") int maxRounds,
                            @Value("${wxclaw.ai.agent.direct-tool-fill:true}") boolean directToolFill) {
        this.detectors = detectors;
        this.webSearchDetector = webSearchDetector;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRounds = Math.max(1, maxRounds);
        this.directToolFill = directToolFill;
    }

    public AgentChatResult run(String userMessage, String firstPrompt, AgentLlmCaller llmCaller) {
        List<AiToolInvocationStore.Invocation> allInvocations = new ArrayList<>();
        List<AgentChatRound> rounds = new ArrayList<>();

        AgentLlmCaller.LlmCallResult first = llmCaller.call(firstPrompt);
        String finalContent = first.content();
        allInvocations.addAll(first.invocations());
        rounds.add(new AgentChatRound(1, "llm", firstPrompt, first.content(), first.invocations(), null));

        if (enabled && maxRounds > 1) {
            for (int round = 2; round <= maxRounds; round++) {
                List<ToolRequirement> missing = detectMissingRequirements(userMessage, allInvocations);
                if (missing.isEmpty()) {
                    break;
                }

                List<AiToolInvocationStore.Invocation> filled = directToolFill ? fillMissingTools(userMessage, missing) : List.of();
                allInvocations.addAll(filled);

                String supplementPrompt = buildSupplementPrompt(userMessage, finalContent, allInvocations, missing, filled);
                AgentLlmCaller.LlmCallResult next = llmCaller.call(supplementPrompt);
                finalContent = next.content();
                allInvocations.addAll(next.invocations());

                Map<String, Object> reason = new LinkedHashMap<>();
                reason.put("missing", missing.stream().map(ToolRequirement::toolName).toList());
                reason.put("directToolFill", directToolFill);
                reason.put("directFilled", filled.stream().map(AiToolInvocationStore.Invocation::toolName).toList());
                rounds.add(new AgentChatRound(round, "agent_supplement", supplementPrompt, next.content(), next.invocations(), reason));
            }
        }

        boolean completed = detectMissingRequirements(userMessage, allInvocations).isEmpty();
        return new AgentChatResult(finalContent, allInvocations, rounds, completed);
    }

    public String toJsonSafely(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
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

    private List<ToolRequirement> detectMissingRequirements(String userMessage, List<AiToolInvocationStore.Invocation> invocations) {
        String text = userMessage == null ? "" : userMessage.trim();
        if (text.isBlank()) {
            return List.of();
        }

        Set<String> calledTools = invocations == null ? Set.of() : invocations.stream()
                .filter(Objects::nonNull)
                .map(AiToolInvocationStore.Invocation::toolName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ToolRequirement> missing = new ArrayList<>();
        for (ToolRequirementDetector detector : detectors) {
            ToolRequirement requirement = detector.detect(userMessage, calledTools);
            if (requirement != null) {
                missing.add(requirement);
            }
        }

        return missing;
    }

    private List<AiToolInvocationStore.Invocation> fillMissingTools(String userMessage, List<ToolRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }

        List<AiToolInvocationStore.Invocation> result = new ArrayList<>();
        for (ToolRequirement requirement : requirements) {
            // 特殊处理 web_search，因为它需要 userMessage
            if ("web_search".equals(requirement.toolName())) {
                String query = webSearchDetector.buildSearchQuery(userMessage);
                AiToolInvocationStore.Invocation invocation = webSearchDetector.executeSearch(query);
                if (invocation != null) {
                    result.add(invocation);
                }
                continue;
            }

            // 其他工具通过 detector 填充
            for (ToolRequirementDetector detector : detectors) {
                if (detector.getToolName().equals(requirement.toolName()) ||
                    (requirement.toolName().startsWith(detector.getToolName()))) {
                    AiToolInvocationStore.Invocation invocation = detector.fillTool(requirement);
                    if (invocation != null) {
                        result.add(invocation);
                    }
                    break;
                }
            }
        }

        return result;
    }

    private String buildSupplementPrompt(String userMessage,
                                         String previousAnswer,
                                         List<AiToolInvocationStore.Invocation> allInvocations,
                                         List<ToolRequirement> missing,
                                         List<AiToolInvocationStore.Invocation> filled) {
        StringBuilder sb = new StringBuilder();
        sb.append("你刚才的回答可能不完整。请基于补充工具结果，重新给出完整、自然、简洁的中文回答。\n");
        sb.append("不要解释工具调用过程，不要说自己之前漏查。\n\n");
        sb.append("原始用户问题：\n").append(TextSanitizer.sanitizeForPrompt(userMessage)).append("\n\n");
        sb.append("上一轮回答：\n").append(TextSanitizer.sanitizeForPrompt(previousAnswer)).append("\n\n");
        sb.append("系统判断缺少的工具：\n").append(toJsonSafely(missing)).append("\n\n");
        if (filled != null && !filled.isEmpty()) {
            sb.append("已由系统补充调用的工具结果：\n").append(toJsonSafely(filled)).append("\n\n");
        }
        sb.append("全部已知工具调用记录：\n").append(toJsonSafely(allInvocations)).append("\n\n");
        sb.append("请直接给用户最终回复。");
        return sb.toString();
    }
}
