package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.model.*;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.prompt.PromptLoader;
import com.dust.wxclawbackfront.exception.AgentPlanningException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排器
 * 只规划高层任务（chat / voice_synthesize / image_generate），
 * 底层工具（天气、搜索等）由 chat 模型通过 Spring AI function calling 自主调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final PlainTextLlmService plainTextLlmService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final PlanValidator planValidator;
    private final PromptLoader promptLoader;

    @Value("${wxclaw.agent.plan.max-retries:3}")
    private int maxRetries = 3;

    @Value("${wxclaw.agent.plan.max-history-messages:10}")
    private int maxPlanningHistoryMessages = 10;

    @Value("${wxclaw.agent.plan.max-history-chars:6000}")
    private int maxPlanningHistoryChars = 6000;

    private boolean looksLikeImageGeneration(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        boolean imageWord = message.contains("图片")
                || message.contains("图")
                || message.contains("image")
                || message.contains("画");
        boolean generationWord = message.contains("生成")
                || message.contains("画")
                || message.contains("一张")
                || message.contains("发一张")
                || message.contains("来一张")
                || message.contains("做一张")
                || message.contains("整一张")
                || message.contains("create")
                || message.contains("generate");
        return imageWord && generationWord;
    }

    /**
     * Agent 执行入口
     */
    public AgentResult orchestrate(String userMessage, AgentContext context) {
        log.info("Agent 开始处理: userId={}, message={}", context.getUserId(),
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        try {
            // Phase 1: 让 LLM 规划高层任务执行计划（所有消息统一走规划模型，意图判断交由模型完成）
            TaskPlan plan = planWithLlm(userMessage, context);
            log.info("任务规划完成: steps={}", plan.getStepCount());

            // Phase 2: 执行任务
            List<TaskResult> results = taskExecutor.execute(plan, context);

            // Phase 3: 合成结果
            AgentResult agentResult = synthesizeResults(results, plan);

            log.info("Agent 处理完成: success={}", agentResult.isSuccess());
            return agentResult;

        } catch (AgentPlanningException e) {
            log.error("Agent 规划异常（类型：{}）: {}", e.getCode(), e.getMessage(), e);
            return AgentResult.failure("任务规划失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Agent 处理异常: {}", e.getMessage(), e);
            return AgentResult.failure("处理失败: " + e.getMessage());
        }
    }

    /**
     * 对已经明确属于普通对话处理的请求直接执行 chat，避免再次调用规划模型。
     */
    public AgentResult orchestrateChat(AgentContext context) {
        try {
            TaskPlan plan = TaskPlan.chat();
            List<TaskResult> results = taskExecutor.execute(plan, context);
            return synthesizeResults(results, plan);
        } catch (Exception exception) {
            log.error("直接对话处理异常: {}", exception.getMessage(), exception);
            return AgentResult.failure("处理失败: " + exception.getMessage());
        }
    }

    /**
     * 让 LLM 规划高层任务执行计划
     */
    private TaskPlan planWithLlm(String userMessage, AgentContext context) {
        String prompt = buildPlanningPrompt(userMessage, context);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = plainTextLlmService.chat(prompt, "PLAN");
                log.info("规划模型原始响应: {}", response);
                String json = normalizePlanJson(extractJson(response));
                log.info("规划模型提取后的JSON: {}", json);

                PlanValidator.ValidationResult validation = planValidator.validate(json);
                if (validation.isValid()) {
                    return parsePlanResponse(json);
                }

                log.warn("规划验证失败，重试 {}/{}: {}", attempt, maxRetries, validation.getError());

                if (attempt == maxRetries) {
                    log.warn("规划重试耗尽，降级为 chat 模式");
                    return fallbackPlan(userMessage);
                }
            } catch (AgentPlanningException e) {
                log.error("规划异常（类型：{}），重试 {}/{}: {}", e.getCode(), attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    return fallbackPlan(userMessage);
                }
            } catch (Exception e) {
                log.warn("规划异常，重试 {}/{}: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    return fallbackPlan(userMessage);
                }
            }
        }

        return fallbackPlan(userMessage);
    }

    private TaskPlan fallbackPlan(String userMessage) {
        return TaskPlan.chat();
    }

    String buildPlanningPrompt(String userMessage, AgentContext context) {
        String historyText = buildPlanningHistory(context);

        Map<String, String> variables = new HashMap<>();
        variables.put("user_message", userMessage);
        variables.put("history", historyText);

        StringBuilder profilesText = new StringBuilder();
        if (context.getUserProfiles() != null) {
            context.getUserProfiles().forEach(p ->
                    profilesText.append(p.getKeyName()).append(": ").append(p.getKeyValue()).append("\n"));
        }
        String profiles = profilesText.toString();
        variables.put("user_profiles", profiles);

        Map<String, Boolean> sections = new HashMap<>();
        sections.put("history", historyText.length() > 0);
        sections.put("user_profiles", profiles.length() > 0);

        return promptLoader.render("agent-planner", variables, sections);
    }

    /**
     * 拼接规划用历史对话（每条一行、结尾带换行；无历史时返回空串）。
     */
    private String buildPlanningHistory(AgentContext context) {
        List<com.dust.wxclawbackfront.bot.dao.entity.AiMessage> history = context.getHistoryMessages();
        if (history == null || history.isEmpty()) {
            return "";
        }
        List<com.dust.wxclawbackfront.bot.dao.entity.AiMessage> sorted = history.stream()
                .filter(message -> message != null && message.getContent() != null && !message.getContent().isBlank())
                .sorted(Comparator.comparing(com.dust.wxclawbackfront.bot.dao.entity.AiMessage::getMessageSeq,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        int from = Math.max(0, sorted.size() - Math.max(1, maxPlanningHistoryMessages));
        int remaining = maxPlanningHistoryChars > 0 ? maxPlanningHistoryChars : Integer.MAX_VALUE;
        StringBuilder historyText = new StringBuilder();
        for (int i = from; i < sorted.size() && remaining > 0; i++) {
            var message = sorted.get(i);
            String content = message.getContent().trim();
            if (content.length() > remaining) {
                content = content.substring(0, remaining) + "...";
            }
            String role = Integer.valueOf(0).equals(message.getMessageType()) ? "用户" : "助手";
            historyText.append(role).append(": ").append(content).append("\n");
            remaining -= content.length() + role.length() + 3;
        }
        return historyText.toString();
    }

    /**
     * 解析 LLM 返回的任务计划
     */
    private TaskPlan parsePlanResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            JsonNode stepsNode = node.get("steps");

            if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
                return TaskPlan.chat();
            }

            List<TaskStep> steps = new ArrayList<>();
            for (JsonNode stepNode : stepsNode) {
                Map<String, Object> params = parseParams(stepNode.get("params"));

                TaskStep step = TaskStep.builder()
                        .stepNumber(stepNode.has("step") ? stepNode.get("step").asInt() : steps.size() + 1)
                        .toolName(stepNode.has("tool") ? stepNode.get("tool").asText() : "chat")
                        .params(params)
                        .dependsOn(resolveDependency(stepNode.get("depends_on")))
                        .description(stepNode.has("description") ? stepNode.get("description").asText() : null)
                        .build();
                steps.add(step);
            }

            return TaskPlan.builder()
                    .steps(steps)
                    .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("JSON 解析失败，降级为对话: {}", e.getMessage());
            throw new AgentPlanningException("JSON 解析失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.warn("解析任务计划失败，降级为对话: {}", e.getMessage());
            return TaskPlan.chat();
        }
    }

    /**
     * 合成最终结果
     */
    private AgentResult synthesizeResults(List<TaskResult> results, TaskPlan plan) {
        if (results == null || results.isEmpty()) {
            return AgentResult.failure("无执行结果");
        }

        // 构建执行步骤 trace
        List<String> executedSteps = plan.getSteps().stream()
                .map(step -> "Step " + step.getStepNumber() + ": " + step.getToolName()
                        + (step.getDescription() != null ? " - " + step.getDescription() : ""))
                .toList();

        // 单步任务：直接返回结果
        if (results.size() == 1) {
            AgentResult result = convertToAgentResult(results.get(0));
            result.setExecutedSteps(executedSteps);
            return result;
        }

        // 多步任务：合并结果
        TaskResult merged = taskExecutor.mergeResults(results);
        AgentResult result = convertToAgentResult(merged);
        result.setExecutedSteps(executedSteps);
        return result;
    }

    /**
     * 将 TaskResult 转换为 AgentResult
     */
    private AgentResult convertToAgentResult(TaskResult taskResult) {
        if (taskResult == null) {
            return AgentResult.failure("执行结果为空");
        }

        if (!taskResult.isSuccess()) {
            return AgentResult.failure(taskResult.getErrorMessage());
        }

        if (taskResult.getMediaAttachments() != null && !taskResult.getMediaAttachments().isEmpty()) {
            return AgentResult.successWithMedia(taskResult.getTextResult(), taskResult.getMediaAttachments());
        }

        if (taskResult.hasMedia()) {
            return AgentResult.successWithMedia(
                    taskResult.getTextResult(),
                    taskResult.getMediaBytes(),
                    taskResult.getMediaType(),
                    taskResult.getMediaFileName()
            );
        }

        return AgentResult.success(taskResult.getTextResult());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(JsonNode paramsNode) {
        if (paramsNode == null || paramsNode.isNull()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.convertValue(paramsNode, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Integer resolveDependency(JsonNode dependencyNode) {
        if (dependencyNode == null || dependencyNode.isNull()) {
            return null;
        }
        if (dependencyNode.isArray()) {
            if (dependencyNode.isEmpty()) {
                return null;
            }
            return resolveDependency(dependencyNode.get(0));
        }
        return dependencyNode.asInt();
    }

    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        String trimmed = response.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        int arrayStart = trimmed.indexOf("[");
        int arrayEnd = trimmed.lastIndexOf("]");
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    /**
     * 兼容模型返回单个 step 对象而不是 steps 数组的情况。
     */
    private String normalizePlanJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return normalizePlanNode(node);
        } catch (Exception ignored) {
            List<JsonNode> nodes = extractObjectSequence(json);
            if (!nodes.isEmpty()) {
                return wrapSteps(nodes);
            }
        }
        return "{}";
    }

    private String normalizePlanNode(JsonNode node) {
        try {
            if (node.isObject() && node.has("steps")) {
                return objectMapper.writeValueAsString(node);
            }
            if (node.isObject() && (node.has("tool") || node.has("step"))) {
                return wrapSteps(List.of(node));
            }
            if (node.isArray()) {
                return wrapSteps(objectMapper.convertValue(node, List.class).stream()
                        .map(objectMapper::valueToTree)
                        .toList());
            }
        } catch (Exception ignored) {
            return "{}";
        }
        return "{}";
    }

    private List<JsonNode> extractObjectSequence(String text) {
        List<JsonNode> nodes = new ArrayList<>();
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        String[] parts = cleaned.split("(?<=\\})\\s*,\\s*(?=\\{)");
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.isBlank()) {
                continue;
            }
            try {
                nodes.add(objectMapper.readTree(candidate));
            } catch (Exception ignored) {
                // ignore non-JSON fragments
            }
        }
        return nodes;
    }

    private String wrapSteps(List<JsonNode> nodes) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode steps = root.putArray("steps");
            nodes.forEach(steps::add);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
