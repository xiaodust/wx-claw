package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CareerQueryNormalizer {
    private final PlainTextLlmService llm;
    private final ObjectMapper objectMapper;

    public CareerQueryNormalizer(PlainTextLlmService llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    public NormalizedQuery normalize(String userText, NormalizedQuery fallback) {
        if (userText == null || userText.isBlank()) return fallback;
        String prompt = "你是招聘搜索条件解析器。把用户岗位查询转换成严格JSON，不要解释。"
                + "只输出 locations、include_keywords、exclude_keywords、employment_types、published_within_days。"
                + "employment_types 只能是 SOCIAL、CAMPUS、INTERNSHIP；没有条件用空数组或null。"
                + "不要把‘岗位、职位、招聘、实习’等泛化词放入关键词；技术词和公司名保留。用户输入：\n" + userText;
        try {
            String response = llm.chat(prompt, "CAREER_QUERY_NORMALIZE");
            JsonNode node = objectMapper.readTree(extractJson(response));
            Integer publishedWithinDays = node.has("published_within_days") && !node.get("published_within_days").isNull()
                    ? Integer.valueOf(node.get("published_within_days").asInt()) : fallback.publishedWithinDays();
            return new NormalizedQuery(strings(node, "locations"), strings(node, "include_keywords"),
                    strings(node, "exclude_keywords"), strings(node, "employment_types"),
                    publishedWithinDays);
        } catch (Exception exception) {
            log.warn("岗位查询条件AI解析失败，使用原始条件: {}", exception.getMessage());
            return fallback;
        }
    }

    private List<String> strings(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return result;
        if (value.isArray()) value.forEach(item -> { if (!item.asText().isBlank()) result.add(item.asText().trim()); });
        else if (!value.asText().isBlank()) result.add(value.asText().trim());
        return result;
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        return start >= 0 && end > start ? response.substring(start, end + 1) : "{}";
    }

    public record NormalizedQuery(List<String> locations, List<String> includeKeywords,
                                  List<String> excludeKeywords, List<String> employmentTypes,
                                  Integer publishedWithinDays) {
    }
}
