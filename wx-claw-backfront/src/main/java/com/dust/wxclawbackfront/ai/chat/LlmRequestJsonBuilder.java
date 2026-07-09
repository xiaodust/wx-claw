package com.dust.wxclawbackfront.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 请求 JSON 构建器
 * 负责生成标准的 LLM 请求 JSON 结构（用于 trace）
 */
@Component
@RequiredArgsConstructor
public class LlmRequestJsonBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 构建纯文本请求的 JSON 表示
     * @param model 模型名称
     * @param text 请求文本
     * @param thinkingType 思考模式类型
     * @return JSON 字符串
     */
    public String buildTextOnlyRequestJson(String model, String text, String thinkingType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            if (thinkingType != null && !thinkingType.isBlank()) {
                payload.put("thinking", Map.of("type", thinkingType.trim()));
            }
            Map<String, Object> contentItem = new LinkedHashMap<>();
            contentItem.put("type", "input_text");
            contentItem.put("text", text);
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("role", "user");
            inputItem.put("content", List.of(contentItem));
            payload.put("input", List.of(inputItem));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }
}
