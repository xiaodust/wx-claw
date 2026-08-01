package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerTaskService;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerJobRecommendationToolHandler implements ToolHandler {
    private final CareerTools careerTools;

    @Override
    public String getName() {
        return "career_job_recommendation";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        Map<String, Object> params = step.getParams() == null ? Map.of() : step.getParams();
        CareerTaskService.TaskSubmission submission = careerTools.recommendJobsByResume(
                listParam(params, "locations"),
                listParam(params, "include_keywords", "includeKeywords", "keywords"),
                listParam(params, "exclude_keywords", "excludeKeywords"),
                listParam(params, "employment_types", "employmentTypes"),
                integerParam(params, "published_within_days", "publishedWithinDays"),
                integerParam(params, "min_match_score", "minMatchScore"),
                integerParam(params, "top_n", "topN"));
        long duration = Instant.now().toEpochMilli() - started;
        return submission.accepted()
                ? TaskResult.success(submission.message(), duration)
                : TaskResult.failure(submission.message(), duration);
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
