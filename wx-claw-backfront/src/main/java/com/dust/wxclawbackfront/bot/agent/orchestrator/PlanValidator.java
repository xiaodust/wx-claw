package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanValidator {

    private final ObjectMapper objectMapper;

    private static final Set<String> VALID_TOOLS = Set.of(
            "chat", "voice_synthesize", "image_generate", "video_generate"
    );

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

            for (int i = 0; i < stepsNode.size(); i++) {
                JsonNode stepNode = stepsNode.get(i);

                if (!stepNode.has("step")) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 缺少 'step' 字段");
                }

                if (!stepNode.has("tool")) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 缺少 'tool' 字段");
                }

                String tool = stepNode.get("tool").asText();
                if (!VALID_TOOLS.contains(tool)) {
                    return ValidationResult.invalid("步骤 " + (i + 1) + " 的 tool 值无效: " + tool);
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