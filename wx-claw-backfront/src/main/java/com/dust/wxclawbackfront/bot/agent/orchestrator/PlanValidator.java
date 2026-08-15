package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanValidator {

    private final ObjectMapper objectMapper;

    private static final Set<String> VALID_TOOLS = Set.of(
            "chat", "voice_synthesize", "image_generate", "video_generate",
            "career_resume_score", "career_resume_analyze", "career_job_recommendation", "career_job_search", "career_resume_retrieve",
            "career_resume_clear", "knowledge_file_retrieve"
    );

    @Value("${wxclaw.agent.plan.max-steps:5}")
    private int maxSteps = 5;

    public ValidationResult validate(String json) {
        if (json == null || json.isBlank()) {
            return ValidationResult.invalid("JSON 为空");
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            if (!node.has("steps")) {
                return ValidationResult.invalid("缺少 'steps' 字段");
            }

            JsonNode stepsNode = node.get("steps");
            if (!stepsNode.isArray() || stepsNode.isEmpty()) {
                return ValidationResult.invalid("'steps' 必须是非空数组");
            }
            if (stepsNode.size() > maxSteps) {
                return ValidationResult.invalid("步骤数超过限制: " + maxSteps);
            }

            Set<Integer> stepNumbers = new HashSet<>();
            int chatSteps = 0;
            for (int i = 0; i < stepsNode.size(); i++) {
                JsonNode stepNode = stepsNode.get(i);

                if (!stepNode.has("step")) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 缺少 'step' 字段");
                }

                if (!stepNode.has("tool")) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 缺少 'tool' 字段");
                }

                int stepNumber = stepNode.get("step").asInt(-1);
                if (stepNumber <= 0 || !stepNumbers.add(stepNumber)) {
                    return ValidationResult.invalid("步骤编号必须为不重复的正整数: " + stepNumber);
                }
                if (stepNumber != i + 1) {
                    return ValidationResult.invalid("步骤编号必须从 1 开始连续递增");
                }

                String tool = stepNode.get("tool").asText();
                if (!VALID_TOOLS.contains(tool)) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 的 tool 值无效: " + tool);
                }
                if ("chat".equals(tool) && ++chatSteps > 1) {
                    return ValidationResult.invalid("chat 步骤最多只能出现一次");
                }
                if (stepNode.has("params") && !stepNode.get("params").isObject()) {
                    return ValidationResult.invalid("步骤 " + stepNumber + " 的 params 必须是对象");
                }
                if (stepNode.has("depends_on") && !stepNode.get("depends_on").isNull()) {
                    JsonNode dependencyNode = stepNode.get("depends_on");
                    if (dependencyNode.isArray()) {
                        for (JsonNode item : dependencyNode) {
                            int dependency = item.asInt(-1);
                            if (dependency <= 0 || dependency == stepNumber || !stepNumbers.contains(dependency)) {
                                return ValidationResult.invalid("步骤 " + stepNumber + " 的依赖必须指向前置步骤");
                            }
                        }
                    } else {
                        int dependency = dependencyNode.asInt(-1);
                        if (dependency <= 0 || dependency == stepNumber || !stepNumbers.contains(dependency)) {
                            return ValidationResult.invalid("步骤 " + stepNumber + " 的依赖必须指向前置步骤");
                        }
                    }
                }
            }

            return ValidationResult.valid();
        } catch (Exception e) {
            return ValidationResult.invalid("JSON 解析失败: " + e.getMessage());
        }
    }

    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String error;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
    }
}
