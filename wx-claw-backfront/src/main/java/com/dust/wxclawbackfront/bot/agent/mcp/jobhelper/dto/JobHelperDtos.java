package com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public final class JobHelperDtos {
    private JobHelperDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobFilters(
            List<String> locations,
            List<String> employmentTypes,
            List<String> sourceCodes,
            List<String> departments,
            List<String> jobCategories,
            List<String> includeKeywords,
            List<String> excludeKeywords,
            Integer publishedWithinDays,
            Integer minMatchScore,
            Integer topN
    ) {
        public JobFilters {
            locations = copy(locations);
            employmentTypes = copy(employmentTypes);
            sourceCodes = copy(sourceCodes);
            departments = copy(departments);
            jobCategories = copy(jobCategories);
            includeKeywords = copy(includeKeywords);
            excludeKeywords = copy(excludeKeywords);
        }
    }

    public record JobSearchRequest(JobFilters filters, Integer page, Integer pageSize) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredResume(String fileName, String sha256, Instant uploadedAt, long fileSize, String resourceUri) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentResume(boolean exists, StoredResume resume, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeleteResumeResult(boolean deleted) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobSearchResponse(
            String requestId,
            int page,
            int pageSize,
            boolean hasNext,
            Instant jobDataSeenAt,
            List<JobView> jobs,
            boolean cached
    ) {
        public JobSearchResponse {
            jobs = copy(jobs);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobView(
            long jobId,
            String sourceCode,
            String sourceJobId,
            String title,
            String companyName,
            String department,
            String jobCategory,
            String employmentType,
            List<String> locations,
            String description,
            String requirements,
            Instant publishTime,
            Instant lastSeenAt,
            String detailUrl
    ) {
        public JobView {
            locations = copy(locations);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResumeScoreResponse(String requestId, JsonNode data, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobRecommendationResponse(
            String requestId,
            JsonNode resumeProfile,
            RecommendationSummary summary,
            List<JobRecommendation> recommendations,
            boolean cached
    ) {
        public JobRecommendationResponse {
            recommendations = copy(recommendations);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendationSummary(
            int candidateCount,
            int matchedCount,
            Instant jobDataSeenAt,
            String algorithmVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobRecommendation(
            JobView job,
            int matchScore,
            ScoreBreakdown scoreBreakdown,
            List<String> matchedSkills,
            List<String> gaps,
            List<String> reasons
    ) {
        public JobRecommendation {
            matchedSkills = copy(matchedSkills);
            gaps = copy(gaps);
            reasons = copy(reasons);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreBreakdown(
            int skill,
            int experience,
            int titleAndCategory,
            int preference,
            int educationAndCertificate,
            int freshness,
            int completeness
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorResponse(String requestId, String code, String message, List<String> details) {
        public ErrorResponse {
            details = copy(details);
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
