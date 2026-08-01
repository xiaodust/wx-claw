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

    @Value("${wxclaw.agent.plan.max-retries:3}")
    private int maxRetries;

    @Value("${wxclaw.agent.plan.max-history-messages:10}")
    private int maxPlanningHistoryMessages;

    @Value("${wxclaw.agent.plan.max-history-chars:6000}")
    private int maxPlanningHistoryChars;

    /**
     * Agent 执行入口
     */
    public AgentResult orchestrate(String userMessage, AgentContext context) {
        log.info("Agent 开始处理: userId={}, message={}", context.getUserId(),
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        try {
            if (!requiresHighLevelPlanning(userMessage)) {
                return orchestrateChat(context);
            }
            // Phase 1: 让 LLM 规划高层任务执行计划
            TaskPlan plan = normalizeCareerPlan(planWithLlm(userMessage, context), userMessage);
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

    boolean requiresHighLevelPlanning(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return false;
        String text = userMessage.toLowerCase();
        boolean voice = text.contains("语音回复") || text.contains("用语音") || text.contains("读给我听")
                || text.contains("朗读");
        boolean image = text.contains("生成图片") || text.contains("生成一张图") || text.contains("画一张")
                || text.contains("画个");
        boolean video = text.contains("生成视频") || text.contains("做个视频") || text.contains("制作视频");
        boolean career = text.contains("简历") || text.contains("岗位") || text.contains("职位")
                || text.contains("招聘") || text.contains("校招") || text.contains("社招") || text.contains("实习");
        boolean storedFile = (text.contains("知识库") || text.contains("原始文件"))
                && (text.contains("取回") || text.contains("发给我") || text.contains("发送给我"));
        return voice || image || video || career || storedFile;
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
                String json = extractJson(response);

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
        if (userMessage == null) return TaskPlan.chat();
        String text = userMessage.toLowerCase();
        String tool = null;
        if (text.contains("简历") && (text.contains("删除") || text.contains("清除") || text.contains("忘记"))) {
            tool = "career_resume_clear";
        } else if (text.contains("简历") && (text.contains("发给我") || text.contains("发送给我") || text.contains("取回"))) {
            tool = "career_resume_retrieve";
        } else if (text.contains("简历") && (text.contains("评分") || text.contains("打分") || text.contains("评估"))) {
            tool = "career_resume_score";
        } else if ((text.contains("简历") || text.contains("经历") || text.contains("技能"))
                && (text.contains("岗位") || text.contains("职位"))
                && (text.contains("推荐") || text.contains("匹配"))) {
            tool = "career_job_recommendation";
        } else if (text.contains("岗位") || text.contains("职位") || text.contains("招聘")
                || text.contains("校招") || text.contains("社招") || text.contains("实习")) {
            tool = "career_job_search";
        } else if (text.contains("简历")) {
            tool = "career_resume_analyze";
        }
        if (tool == null) return TaskPlan.chat();
        return TaskPlan.builder().steps(List.of(TaskStep.builder()
                .stepNumber(1).toolName(tool).params(new HashMap<>()).description("规划失败后的安全兜底").build())).build();
    }

    private TaskPlan normalizeCareerPlan(TaskPlan plan, String userMessage) {
        if (plan == null || userMessage == null || !isGeneralJobSearch(userMessage)) {
            return plan;
        }
        plan.getSteps().stream()
                .filter(step -> "career_job_recommendation".equals(step.getToolName()))
                .forEach(step -> step.setToolName("career_job_search"));
        return plan;
    }

    private boolean isGeneralJobSearch(String userMessage) {
        String text = userMessage.toLowerCase();
        boolean jobQuery = text.contains("岗位") || text.contains("职位") || text.contains("招聘")
                || text.contains("开发岗") || text.contains("工程师");
        boolean personalized = text.contains("简历") || text.contains("履历") || text.contains("经历")
                || text.contains("技能匹配") || text.contains("根据我") || text.contains("适合我");
        return jobQuery && !personalized;
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
        sb.append("- career_resume_score: 对用户最近上传的 PDF 简历进行评分。\n");
        sb.append("  参数: job_description（可选；用户提供岗位 JD 时传入）\n\n");
        sb.append("- career_job_recommendation: 根据用户最近上传的 PDF 简历推荐匹配岗位。\n");
        sb.append("  参数: locations、include_keywords、exclude_keywords、employment_types、published_within_days、min_match_score、top_n\n");
        sb.append("  employment_types 只能使用 INTERNSHIP、CAMPUS、SOCIAL\n\n");
        sb.append("- career_resume_retrieve: 用户明确索要自己已保存的简历 PDF 时使用。\n\n");
        sb.append("- career_resume_analyze: 基于已保存简历完成写作、总结、分析、整理等非评分/岗位推荐任务。\n\n");
        sb.append("- career_resume_clear: 用户要求删除、清除或忘记已保存简历时使用。\n\n");
        sb.append("- knowledge_file_retrieve: 用户明确要求取回或发送已存知识库原始文件时使用；简历的 label 使用 resume。\n\n");
        sb.append("- career_job_search: 搜索真实在招岗位，不需要简历。\n");
        sb.append("  参数: locations、include_keywords、exclude_keywords、employment_types、published_within_days、page、page_size\n\n");
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
        sb.append("11. 【职业任务】用户要求给简历评分、打分或评估 → 只使用 career_resume_score，不要使用 chat\n");
        sb.append("12. 【职业任务】用户要求根据简历、履历、经历或技能推荐/匹配岗位 → 只使用 career_job_recommendation，绝对不要使用 career_resume_score 或 chat\n");
        sb.append("12.1 用户要求根据简历写作、总结、分析或整理，但不是评分或岗位推荐 → 只使用 career_resume_analyze\n");
        sb.append("13. career_job_recommendation 必须从用户原话提取城市、关键词和实习/校招/社招类型；未明确的参数使用空数组或省略\n\n");
        sb.append("16. 用户只询问某公司、城市、岗位名称或关键词的在招岗位（如“推荐一些腾讯开发岗”），即使使用“推荐”二字，也必须使用 career_job_search，不得调用 career_job_recommendation。只有用户明确说“根据我的简历/经历/技能匹配”时，才使用 career_job_recommendation。\n\n");
        sb.append("14. 必须结合历史对话理解省略主语和追问，例如“扩大到全国”“换个城市”“再多找几个”“只要社招”。这些表达是对最近一次职业任务的参数更新，不是新的普通问答。\n");
        sb.append("15. 追问参数合并：本轮明确条件覆盖历史条件；本轮未提到的条件继承最近一次职业任务；“扩大到全国/全国范围”将 locations 设为 [\"全国\"]。若历史没有职业任务，再仅依据本轮内容判断。\n\n");
        sb.append("## 用户消息\n\n");
        sb.append(userMessage).append("\n\n");

        appendPlanningHistory(sb, context);

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

    private void appendPlanningHistory(StringBuilder prompt, AgentContext context) {
        List<com.dust.wxclawbackfront.bot.dao.entity.AiMessage> history = context.getHistoryMessages();
        if (history == null || history.isEmpty()) {
            return;
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
        if (historyText.length() > 0) {
            prompt.append("## 历史对话（仅用于解析当前追问）\n\n")
                    .append(historyText).append("\n");
        }
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
