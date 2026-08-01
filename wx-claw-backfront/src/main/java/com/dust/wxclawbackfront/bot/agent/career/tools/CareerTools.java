package com.dust.wxclawbackfront.bot.agent.career.tools;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpException;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerReplyFormatter;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerTools implements AiToolProvider {
    private static final Set<String> KNOWN_COMPANIES = Set.of("腾讯", "阿里", "阿里巴巴", "字节跳动", "字节", "百度", "华为", "京东", "美团", "拼多多", "小米", "网易");
    private final JobHelperMcpClient client;
    private final CareerTaskService taskService;
    private final CareerResumeContextStore resumeStore;
    private final CareerReplyFormatter formatter;
    private final AiToolInvocationStore invocationStore;

    public CareerTools(JobHelperMcpClient client,
                       CareerTaskService taskService,
                       CareerResumeContextStore resumeStore,
                       CareerReplyFormatter formatter,
                       AiToolInvocationStore invocationStore) {
        this.client = client;
        this.taskService = taskService;
        this.resumeStore = resumeStore;
        this.formatter = formatter;
        this.invocationStore = invocationStore;
    }

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 25;
    }

    @Override
    public boolean isAvailableToChat() {
        return false;
    }

    @Tool(name = "search_jobs", description = "搜索真实招聘岗位。用户询问在招职位、招聘岗位、某城市或公司的岗位时必须使用本工具，不得使用 web_search 替代。无需简历。")
    public CareerReplyFormatter.SearchToolResult searchJobs(
            @ToolParam(description = "城市列表，如杭州、上海") List<String> locations,
            @ToolParam(description = "包含关键词，如Java、后端") List<String> includeKeywords,
            @ToolParam(description = "排除关键词") List<String> excludeKeywords,
            @ToolParam(description = "用工类型：SOCIAL、CAMPUS、INTERNSHIP") List<String> employmentTypes,
            @ToolParam(description = "只看最近多少天发布的岗位") Integer publishedWithinDays,
            @ToolParam(description = "页码，从1开始") Integer page,
            @ToolParam(description = "每页数量，最大20") Integer pageSize) {
        JobHelperDtos.JobFilters filters = filters(locations, includeKeywords, excludeKeywords,
                employmentTypes, publishedWithinDays, null, null);
        int safePage = page == null ? 1 : Math.max(1, page);
        int safePageSize = pageSize == null ? 10 : Math.max(1, Math.min(20, pageSize));
        try {
            JobHelperDtos.JobSearchResponse response = client.search(filters, safePage, safePageSize);
            response = filterKnownCompany(response, filters.includeKeywords());
            CareerReplyFormatter.SearchToolResult result = formatter.compactSearch(response);
            invocationStore.add("search_jobs", filterSummary(filters, safePage, safePageSize),
                    "success=true, requestId=" + result.requestId() + ", jobs=" + result.jobs().size());
            return result;
        } catch (JobHelperMcpException exception) {
            invocationStore.add("search_jobs", filterSummary(filters, safePage, safePageSize),
                    "success=false, code=" + exception.getCode() + ", requestId=" + exception.getRequestId());
            return new CareerReplyFormatter.SearchToolResult(false, exception.getRequestId(), safePage, false,
                    null, List.of(), formatter.formatFailure("岗位搜索", exception.getCode(),
                    exception.getMessage(), exception.getRequestId()));
        }
    }

    private JobHelperDtos.JobSearchResponse filterKnownCompany(JobHelperDtos.JobSearchResponse response,
                                                                 List<String> keywords) {
        List<String> companies = clean(keywords).stream()
                .filter(KNOWN_COMPANIES::contains).toList();
        if (companies.isEmpty() || response == null || response.jobs() == null) return response;
        List<JobHelperDtos.JobView> filtered = response.jobs().stream()
                .filter(job -> job.companyName() != null && companies.stream()
                        .anyMatch(company -> job.companyName().contains(company)))
                .toList();
        return new JobHelperDtos.JobSearchResponse(response.requestId(), response.page(), response.pageSize(),
                response.hasNext(), response.jobDataSeenAt(), filtered, response.cached());
    }

    @Tool(name = "score_resume", description = "对用户最近上传的PDF简历评分。可选传入用户明确提供的岗位JD进行定向评分；没有PDF时会提示用户先上传。")
    public CareerTaskService.TaskSubmission scoreResume(
            @ToolParam(description = "可选岗位JD；通用评分时留空", required = false) String jobDescription) {
        CareerTaskService.TaskSubmission result = taskService.submitScore(jobDescription);
        invocationStore.add("score_resume", "hasJobDescription=" + (jobDescription != null && !jobDescription.isBlank()),
                "accepted=" + result.accepted() + ", taskId=" + result.taskId());
        return result;
    }

    @Tool(name = "recommend_jobs_by_resume", description = "根据用户最近上传的PDF简历推荐真实岗位。用户要求按简历、经历或技能进行个性化岗位推荐时使用；没有PDF时必须提示先上传，不能用普通搜索伪装。")
    public CareerTaskService.TaskSubmission recommendJobsByResume(
            @ToolParam(description = "城市列表，如杭州、上海") List<String> locations,
            @ToolParam(description = "包含关键词，如Java、后端") List<String> includeKeywords,
            @ToolParam(description = "排除关键词") List<String> excludeKeywords,
            @ToolParam(description = "用工类型：SOCIAL、CAMPUS、INTERNSHIP") List<String> employmentTypes,
            @ToolParam(description = "只看最近多少天发布的岗位") Integer publishedWithinDays,
            @ToolParam(description = "最低匹配分，0到100") Integer minMatchScore,
            @ToolParam(description = "推荐数量，默认5，最大10") Integer topN) {
        int safeTopN = topN == null ? 5 : Math.max(1, Math.min(10, topN));
        Integer safeScore = minMatchScore == null ? null : Math.max(0, Math.min(100, minMatchScore));
        JobHelperDtos.JobFilters filters = filters(locations, includeKeywords, excludeKeywords,
                employmentTypes, publishedWithinDays, safeScore, safeTopN);
        CareerTaskService.TaskSubmission result = taskService.submitRecommendation(filters);
        invocationStore.add("recommend_jobs_by_resume", filterSummary(filters, 1, safeTopN),
                "accepted=" + result.accepted() + ", taskId=" + result.taskId());
        return result;
    }

    @Tool(name = "clear_resume", description = "删除当前用户为简历评分或岗位推荐临时上传的PDF简历。用户说删除、清除或忘记我的简历时使用。")
    public ClearResumeResult clearResume() {
        boolean cleared = resumeStore.clearCurrent();
        invocationStore.add("clear_resume", "currentUser", "cleared=" + cleared);
        return new ClearResumeResult(cleared, cleared ? "临时简历已删除。" : "当前没有临时保存的简历。");
    }

    private JobHelperDtos.JobFilters filters(List<String> locations,
                                               List<String> includeKeywords,
                                               List<String> excludeKeywords,
                                               List<String> employmentTypes,
                                               Integer publishedWithinDays,
                                               Integer minMatchScore,
                                               Integer topN) {
        return new JobHelperDtos.JobFilters(clean(locations), normalizeEmploymentTypes(inferEmploymentTypes(employmentTypes, includeKeywords)),
                List.of(), List.of(), List.of(), normalizeIncludeKeywords(includeKeywords), clean(excludeKeywords),
                positive(publishedWithinDays), minMatchScore, topN);
    }

    private List<String> inferEmploymentTypes(List<String> employmentTypes, List<String> keywords) {
        List<String> normalized = normalizeEmploymentTypes(employmentTypes);
        if (!normalized.isEmpty()) return normalized;
        boolean internship = clean(keywords).stream().anyMatch(value ->
                value.contains("实习") || value.toLowerCase(Locale.ROOT).contains("intern"));
        return internship ? List.of("INTERNSHIP") : normalized;
    }

    private List<String> normalizeIncludeKeywords(List<String> values) {
        return clean(values).stream()
                .flatMap(value -> {
                    String normalized = value.replace('，', ',').trim();
                    normalized = normalized.replaceAll("(?i)\\b(intern|internship)\\b", "")
                            .replace("实习生", "").replace("实习", "")
                            .replace("岗位", "").replace("职位", "").replace("岗", "").trim();
                    if (normalized.isBlank()) return java.util.stream.Stream.empty();
                    if (normalized.contains(",")) {
                        return java.util.Arrays.stream(normalized.split(","))
                                .map(String::trim).filter(item -> !item.isBlank());
                    }
                    return java.util.stream.Stream.of(normalized);
                })
                .map(value -> value.equalsIgnoreCase("java开发") ? "Java" : value)
                .distinct().toList();
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private List<String> normalizeEmploymentTypes(List<String> values) {
        return clean(values).stream().map(value -> switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "社招", "社会招聘" -> "SOCIAL";
            case "校招", "校园招聘", "应届" -> "CAMPUS";
            case "实习", "实习生" -> "INTERNSHIP";
            default -> value.trim().toUpperCase(Locale.ROOT);
        }).filter(value -> List.of("SOCIAL", "CAMPUS", "INTERNSHIP").contains(value)).distinct().toList();
    }

    private Integer positive(Integer value) {
        return value == null ? null : Math.max(1, value);
    }

    private String filterSummary(JobHelperDtos.JobFilters filters, int page, int pageSize) {
        return "locations=" + filters.locations() + ", keywords=" + filters.includeKeywords()
                + ", employmentTypes=" + filters.employmentTypes() + ", page=" + page + ", pageSize=" + pageSize;
    }

    public record ClearResumeResult(boolean cleared, String message) {
    }
}
