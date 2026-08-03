package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerQueryNormalizer;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerTaskService;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerJobRecommendationToolHandler implements ToolHandler {
    private final CareerTools careerTools;
    private final CareerQueryNormalizer queryNormalizer;

    @Override
    public String getName() {
        return "career_job_recommendation";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        Map<String, Object> params = step.getParams() == null ? Map.of() : step.getParams();
        List<String> locations = new ArrayList<>(listParam(params, "locations"));
        List<String> includeKeywords = new ArrayList<>(listParam(params, "include_keywords", "includeKeywords", "keywords"));
        List<String> excludeKeywords = new ArrayList<>(listParam(params, "exclude_keywords", "excludeKeywords"));
        List<String> employmentTypes = new ArrayList<>(listParam(params, "employment_types", "employmentTypes"));
        Integer publishedWithinDays = integerParam(params, "published_within_days", "publishedWithinDays");

        // 规划模型可只给 input 分句：缺失的结构化参数由查询归一化器从 input 中提取
        Object inputParam = params.get("input");
        if (inputParam instanceof String input && !input.isBlank() && queryNormalizer != null
                && (locations.isEmpty() || includeKeywords.isEmpty() || employmentTypes.isEmpty())) {
            CareerQueryNormalizer.NormalizedQuery normalized = queryNormalizer.normalize(input,
                    new CareerQueryNormalizer.NormalizedQuery(locations, includeKeywords, excludeKeywords,
                            employmentTypes, publishedWithinDays));
            if (locations.isEmpty()) locations = normalized.locations();
            if (includeKeywords.isEmpty()) includeKeywords = normalized.includeKeywords();
            if (excludeKeywords.isEmpty()) excludeKeywords = normalized.excludeKeywords();
            if (employmentTypes.isEmpty()) employmentTypes = normalized.employmentTypes();
            if (publishedWithinDays == null) publishedWithinDays = normalized.publishedWithinDays();
        }

        CareerTaskService.TaskSubmission submission = careerTools.recommendJobsByResume(
                locations, includeKeywords, excludeKeywords, employmentTypes,
                publishedWithinDays,
                integerParam(params, "min_match_score", "minMatchScore"),
                integerParam(params, "top_n", "topN"));
        long duration = Instant.now().toEpochMilli() - started;
        return TaskResult.success(submission.message(), duration);
    }

    private List<String> listParam(Map<String, Object> params, String... names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value instanceof Collection<?> collection) {
                return collection.stream().filter(item -> item != null && !item.toString().isBlank())
                        .map(item -> item.toString().trim()).toList();
            }
            if (value != null && !value.toString().isBlank()) {
                return List.of(value.toString().trim());
            }
        }
        return List.of();
    }

    private Integer integerParam(Map<String, Object> params, String... names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value instanceof Number number) return number.intValue();
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
}
