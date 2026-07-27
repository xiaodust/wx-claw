package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.model.*;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.exception.AgentPlanningException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Value("${wxclaw.agent.plan.max-retries:3}")
    private int maxRetries;

    /**
     * Agent 执行入口
     */
    public AgentResult orchestrate(String userMessage, AgentContext context) {
        log.info("Agent 开始处理: userId={}, message={}", context.getUserId(),
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        try {
            // Phase 1: 让 LLM 规划高层任务执行计划
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
     * 让 LLM 规划高层任务执行计划
     */
    private TaskPlan planWithLlm(String userMessage, AgentContext context) {
        String prompt = buildPlanningPrompt(userMessage, context);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = plainTextLlmService.chat(prompt, "PLAN");
                String json = extractJson(response);

                PlanValidator.ValidationResult validation = planValidator.validate(json);
                if (validation.isValid()) {
                    return parsePlanResponse(json);
                }

                log.warn("规划验证失败，重试 {}/{}: {}", attempt, maxRetries, validation.getError());

                if (attempt == maxRetries) {
                    log.warn("规划重试耗尽，降级为 chat 模式");
                    return TaskPlan.chat();
                }
            } catch (AgentPlanningException e) {
                log.error("规划异常（类型：{}），重试 {}/{}: {}", e.getCode(), attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    return TaskPlan.chat();
                }
            } catch (Exception e) {
                log.warn("规划异常，重试 {}/{}: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    return TaskPlan.chat();
                }
            }
        }

        return TaskPlan.chat();
    }

    /**
     * 构建任务规划 prompt
     * 只描述高层动作，底层工具由 chat 模型自行调用
     */
    private String buildPlanningPrompt(String userMessage, AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个任务编排器。根据用户输入，规划高层任务执行步骤。\n\n");
        sb.append("## 可用动作\n\n");
        sb.append("- chat: 普通对话、问答、信息查询。chat 模型内置了天气查询、网页搜索、邮件、提醒等工具，会自主决定调用哪些工具。\n");
        sb.append("  参数: 无需额外参数，模型会根据用户消息自动处理\n\n");
        sb.append("- voice_synthesize: 将文本转为语音回复。\n");
        sb.append("  参数: text（可选，要朗读的文本；不传则自动生成）\n\n");
        sb.append("- image_generate: 生成图片。\n");
        sb.append("  参数: prompt（图片描述）\n\n");
        sb.append("- video_generate: 生成视频。\n");
        sb.append("  参数: prompt（视频描述）, ratio（可选，默认16:9）, duration（可选，秒数，默认5）\n\n");
        sb.append("## 规则\n\n");
        sb.append("1. 分析用户意图，规划最少步骤完成任务\n");
        sb.append("2. 普通对话、闲聊、问答、信息查询、图片相关问题 → 只需 1 步 chat，绝对不要多加步骤\n");
        sb.append("3. 只有用户明确要求\"语音回复\"\"用语音说\"\"读给我听\"等 → 1 步 chat + 1 步 voice_synthesize\n");
        sb.append("4. 用户要求生成图片 → 1 步 image_generate\n");
        sb.append("5. 用户要求生成视频 → 1 步 video_generate\n");
        sb.append("6. 后续步骤可以使用前一步的结果（用 {step_N_result} 引用）\n");
        sb.append("7. 不要规划底层工具调用（如 weather_now、web_search 等），chat 模型会自行处理\n");
        sb.append("8. 【重要】chat 步骤最多出现 1 次，不要重复规划 chat 步骤\n");
        sb.append("9. 【重要】用户发送图片问问题（如\"这是什么\"\"帮我看看\"）→ 只需 1 步 chat，不要加 voice_synthesize\n");
        sb.append("10. 【重要】用户发送视频问问题（如\"这个视频说了什么\"）→ 只需 1 步 chat，不要加 voice_synthesize\n\n");
        sb.append("## 用户消息\n\n");
        sb.append(userMessage).append("\n\n");

        if (context.getUserProfiles() != null && !context.getUserProfiles().isEmpty()) {
            sb.append("## 用户信息\n\n");
            context.getUserProfiles().forEach(p ->
                    sb.append(p.getKeyName()).append(": ").append(p.getKeyValue()).append("\n"));
            sb.append("\n");
        }

        sb.append("## 输出格式\n\n");
        sb.append("请输出 JSON 格式的执行计划：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\"step\": 1, \"tool\": \"chat\", \"params\": {}, \"description\": \"处理用户请求\"},\n");
        sb.append("    {\"step\": 2, \"tool\": \"voice_synthesize\", \"params\": {\"text\": \"{step_1_result}\"}, \"depends_on\": 1, \"description\": \"语音播报\"}\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("只输出 JSON，不要输出其他内容。");

        return sb.toString();
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
                        .dependsOn(stepNode.has("depends_on") && !stepNode.get("depends_on").isNull()
                                ? stepNode.get("depends_on").asInt() : null)
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

    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        String trimmed = response.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }
}
