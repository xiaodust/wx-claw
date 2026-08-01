package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerReplyFormatter;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerQueryNormalizer;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerSearchContextStore;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerJobSearchToolHandler implements ToolHandler {
    private final CareerTools careerTools;
    private final CareerQueryNormalizer queryNormalizer;
    private final CareerSearchContextStore searchContextStore;

    public CareerJobSearchToolHandler(CareerTools careerTools, CareerQueryNormalizer queryNormalizer,
                                      CareerSearchContextStore searchContextStore) {
        this.careerTools = careerTools;
        this.queryNormalizer = queryNormalizer;
        this.searchContextStore = searchContextStore;
    }

    @Override
    public String getName() {
        return "career_job_search";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        Map<String, Object> params = step.getParams() == null ? Map.of() : step.getParams();
        CareerSearchContextStore.SearchState previous = searchContextStore.get(context.getUserId());
        boolean continuation = isContinuation(context.getUserText());
        CareerQueryNormalizer.NormalizedQuery previousQuery = previous == null ? null : previous.query();
        CareerQueryNormalizer.NormalizedQuery fallback = new CareerQueryNormalizer.NormalizedQuery(
                continuation && previousQuery != null ? previousQuery.locations() : listParam(params, "locations"),
                continuation && previousQuery != null ? previousQuery.includeKeywords() : listParam(params, "include_keywords", "includeKeywords", "keywords"),
                continuation && previousQuery != null ? previousQuery.excludeKeywords() : listParam(params, "exclude_keywords", "excludeKeywords"),
                continuation && previousQuery != null ? previousQuery.employmentTypes() : listParam(params, "employment_types", "employmentTypes"),
                continuation && previousQuery != null ? previousQuery.publishedWithinDays() : integerParam(params, "published_within_days", "publishedWithinDays"));
        CareerQueryNormalizer.NormalizedQuery query = continuation && previousQuery != null
                ? previousQuery
                : (queryNormalizer == null ? fallback : queryNormalizer.normalize(context.getUserText(), fallback));
        int requestedPage = integerParam(params, "page") == null ? 1 : integerParam(params, "page");
        int page = continuation && previous != null ? Math.max(requestedPage, previous.page() + 1) : requestedPage;
        CareerReplyFormatter.SearchToolResult result = careerTools.searchJobs(
                query.locations(), query.includeKeywords(), query.excludeKeywords(), query.employmentTypes(),
                query.publishedWithinDays(), page, integerParam(params, "page_size", "pageSize"));
        long duration = Instant.now().toEpochMilli() - started;
        if (!result.success()) {
            return TaskResult.failure(result.errorMessage(), duration);
        }
        searchContextStore.put(context.getUserId(), query, result.page());
        StringBuilder text = new StringBuilder("岗位搜索完成，共找到 ").append(result.jobs().size()).append(" 个岗位：\n");
        for (CareerReplyFormatter.CompactJob job : result.jobs()) {
            text.append("- ").append(job.title()).append(" | ").append(job.companyName()).append(" | ")
                    .append(job.locations()).append(" | ").append(job.detailUrl()).append("\n");
        }
        if (result.hasNext()) text.append("还可以继续说“查看更多”。");
        return TaskResult.success(text.toString(), duration);
    }

    private boolean isContinuation(String text) {
        if (text == null) return false;
        return text.contains("更多") || text.contains("下一页") || text.contains("下页")
                || text.contains("再来") || text.contains("继续找") || text.contains("查看更多");
    }

    private List<String> listParam(Map<String, Object> params, String... names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value instanceof Collection<?> collection) {
                return collection.stream().filter(item -> item != null && !item.toString().isBlank()).map(Object::toString).toList();
            }
            if (value != null && !value.toString().isBlank()) return List.of(value.toString().trim());
        }
        return List.of();
    }

    private Integer integerParam(Map<String, Object> params, String... names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value instanceof Number number) return number.intValue();
            if (value != null) try { return Integer.parseInt(value.toString().trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
}
