package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CareerReplyFormatter {
    private static final int MESSAGE_LIMIT = 1800;
    private static final Set<String> SENSITIVE_SCORE_FIELDS = Set.of(
            "resume", "resumetext", "rawtext", "fulltext", "originaltext", "content",
            "personalinfo", "contact", "phone", "email", "name", "姓名", "电话", "邮箱", "简历正文"
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern NAME_PATTERN = Pattern.compile("姓名\\s*[:：]?\\s*[^，,\\s]+", Pattern.CASE_INSENSITIVE);

    private final JobHelperProperties properties;

    public CareerReplyFormatter(JobHelperProperties properties) {
        this.properties = properties;
    }

    public SearchToolResult compactSearch(JobHelperDtos.JobSearchResponse response) {
        int limit = Math.min(properties.getMaxResultsPerMessage(), response.jobs().size());
        List<CompactJob> jobs = response.jobs().stream().limit(limit).map(this::compactJob).toList();
        return new SearchToolResult(true, response.requestId(), response.page(), response.hasNext(),
                response.jobDataSeenAt() == null ? null : DATE_FORMATTER.format(response.jobDataSeenAt()),
                jobs, null);
    }

    public List<String> formatRecommendations(JobHelperDtos.JobRecommendationResponse response) {
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder("已根据你的简历筛选出 ")
                .append(response.recommendations().size()).append(" 个岗位");
        if (response.summary() != null) {
            current.append("（候选 ").append(response.summary().candidateCount()).append(" 个）");
        }
        current.append("：\n");

        int index = 1;
        for (JobHelperDtos.JobRecommendation recommendation : response.recommendations()) {
            String item = formatRecommendation(index++, recommendation);
            if (current.length() + item.length() > MESSAGE_LIMIT && current.length() > 0) {
                messages.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(item);
        }
        if (!current.isEmpty()) {
            messages.add(current.toString().trim());
        }
        return messages;
    }

    public String formatScore(JobHelperDtos.ResumeScoreResponse response) {
        if (response.data() == null || response.data().isNull() || response.data().isMissingNode()) {
            String content = response.content() == null ? "" : redactScoreContent(response.content().trim());
            if (content.isBlank()) {
                throw new IllegalStateException("评分服务未返回有效结果，requestId=" + response.requestId());
            }
            return "简历评分完成：\n" + content + "\n请求编号：" + response.requestId();
        }
        JsonNode sanitized = sanitize(response.data());
        String report = sanitized.toPrettyString();
        return "简历评分完成：\n" + report + "\n请求编号：" + response.requestId();
    }

    public List<String> formatScoreMessages(JobHelperDtos.ResumeScoreResponse response) {
        List<String> parts = split(formatScore(response), 1500);
        if (parts.size() == 1) return parts;
        List<String> labeled = new ArrayList<>(parts.size());
        for (int index = 0; index < parts.size(); index++) {
            labeled.add("简历评分报告（" + (index + 1) + "/" + parts.size() + "）\n" + parts.get(index));
        }
        return labeled;
    }

    private List<String> split(String text, int limit) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                int lineBreak = text.lastIndexOf('\n', end);
                if (lineBreak > start) end = lineBreak + 1;
            }
            parts.add(text.substring(start, end).trim());
            start = end;
        }
        return parts;
    }

    private String redactScoreContent(String content) {
        String redacted = EMAIL_PATTERN.matcher(content).replaceAll("[邮箱已隐藏]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[手机号已隐藏]");
        return NAME_PATTERN.matcher(redacted).replaceAll("姓名：[已隐藏]");
    }

    public String formatFailure(String operation, String code, String message, String requestId) {
        StringBuilder result = new StringBuilder(operation).append("失败：").append(userMessage(code, message));
        if (requestId != null && !requestId.isBlank()) {
            result.append("\n请求编号：").append(requestId);
        }
        return result.toString();
    }

    private String formatRecommendation(int index, JobHelperDtos.JobRecommendation recommendation) {
        JobHelperDtos.JobView job = recommendation.job();
        StringBuilder item = new StringBuilder("\n").append(index).append(". ")
                .append(value(job == null ? null : job.title(), "未知岗位"))
                .append("｜").append(value(job == null ? null : job.companyName(), "未知公司"))
                .append("｜匹配度 ").append(recommendation.matchScore()).append("\n");
        appendList(item, "匹配", recommendation.matchedSkills());
        appendList(item, "差距", recommendation.gaps());
        appendList(item, "理由", recommendation.reasons());
        if (job != null && job.detailUrl() != null && !job.detailUrl().isBlank()) {
            item.append("链接：").append(job.detailUrl()).append("\n");
        }
        return item.toString();
    }

    private CompactJob compactJob(JobHelperDtos.JobView job) {
        // 压缩结果只保留摘要字段，丢弃完整岗位描述与任职要求，避免回复超长
        return new CompactJob(job.jobId(), job.title(), job.companyName(), job.locations(), job.employmentType(),
                job.publishTime() == null ? null : DATE_FORMATTER.format(job.publishTime()), job.detailUrl(),
                null, null);
    }

    private JsonNode sanitize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = ((ObjectNode) node).objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!SENSITIVE_SCORE_FIELDS.contains(field.getKey().toLowerCase(Locale.ROOT))) {
                    result.set(field.getKey(), sanitize(field.getValue()));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = ((ArrayNode) node).arrayNode();
            node.forEach(child -> result.add(sanitize(child)));
            return result;
        }
        return node.deepCopy();
    }

    private String userMessage(String code, String fallback) {
        if ("RATE_LIMITED".equals(code) || "HTTP_429".equals(code)) {
            return "职业服务当前请求较多，请稍后再试。";
        }
        if ("UNAUTHORIZED".equals(code) || "HTTP_401".equals(code)) {
            return "职业服务配置异常，请联系管理员。";
        }
        if ("JOB_HELPER_UNAVAILABLE".equals(code)) {
            return "职业服务暂不可用，请稍后再试。";
        }
        return fallback == null || fallback.isBlank() ? "职业服务暂时无法处理该请求。" : fallback;
    }

    private void appendList(StringBuilder builder, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            builder.append(label).append("：").append(String.join("、", values.stream().limit(5).toList())).append("\n");
        }
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SearchToolResult(boolean success, String requestId, int page, boolean hasNext,
                                   String jobDataSeenAt, List<CompactJob> jobs, String errorMessage) {
    }

    public record CompactJob(long jobId, String title, String companyName, List<String> locations,
                             String employmentType, String publishTime, String detailUrl,
                             String description, String requirements) {
    }
}
