package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerReplyFormatterTest {
    private final CareerReplyFormatter formatter = new CareerReplyFormatter(new JobHelperProperties());

    @Test
    void compactsSearchWithoutDescriptionAndRequirements() {
        JobHelperDtos.JobView job = job("Java开发", "示例公司", "https://example.test/job/1");
        JobHelperDtos.JobSearchResponse response = new JobHelperDtos.JobSearchResponse(
                "request-1", 1, 20, true, Instant.parse("2026-07-31T00:00:00Z"), List.of(job), false);

        CareerReplyFormatter.SearchToolResult result = formatter.compactSearch(response);

        assertEquals(1, result.jobs().size());
        assertEquals("Java开发", result.jobs().getFirst().title());
        assertFalse(result.toString().contains("完整岗位描述"));
        assertFalse(result.toString().contains("完整任职要求"));
    }

    @Test
    void removesResumeBodyAndContactFromStructuredScore() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JobHelperDtos.ResumeScoreResponse response = new JobHelperDtos.ResumeScoreResponse("request-2",
                mapper.readTree("{\"score\":88,\"resumeText\":\"完整简历\",\"personalInfo\":{\"phone\":\"123\"},\"advice\":[\"补充量化结果\"]}"),
                "不应使用的完整输出");

        String result = formatter.formatScore(response);

        assertTrue(result.contains("88"));
        assertTrue(result.contains("补充量化结果"));
        assertFalse(result.contains("完整简历"));
        assertFalse(result.contains("123"));
        assertFalse(result.contains("不应使用的完整输出"));
    }

    @Test
    void usesTextReportWhenWorkflowDoesNotReturnStructuredData() {
        JobHelperDtos.ResumeScoreResponse response = new JobHelperDtos.ResumeScoreResponse(
                "request-3", null, "姓名：示例用户，电话13800138000，邮箱test@example.com\n综合评分：86\n建议补充量化结果");

        String result = formatter.formatScore(response);

        assertTrue(result.contains("综合评分：86"));
        assertTrue(result.contains("建议补充量化结果"));
        assertTrue(result.contains("request-3"));
        assertFalse(result.contains("13800138000"));
        assertFalse(result.contains("test@example.com"));
        assertFalse(result.contains("示例用户"));
    }

    @Test
    void labelsLongScoreReportWithoutDroppingContent() {
        String content = "第一部分\n" + "内容".repeat(900) + "\n最后一句完整内容。";
        JobHelperDtos.ResumeScoreResponse response = new JobHelperDtos.ResumeScoreResponse("request-4", null, content);

        List<String> messages = formatter.formatScoreMessages(response);

        assertTrue(messages.size() > 1);
        assertTrue(messages.getFirst().startsWith("简历评分报告（1/"));
        assertTrue(messages.getLast().contains("最后一句完整内容。"));
        String combined = String.join("", messages);
        assertTrue(combined.contains("第一部分"));
        assertTrue(combined.contains("request-4"));
        assertFalse(combined.contains("已截断"));
    }

    private JobHelperDtos.JobView job(String title, String company, String url) {
        return new JobHelperDtos.JobView(1, "OFFICIAL", "source-1", title, company,
                null, "后端", "SOCIAL", List.of("杭州"), "完整岗位描述", "完整任职要求",
                Instant.parse("2026-07-30T00:00:00Z"), Instant.parse("2026-07-31T00:00:00Z"), url);
    }
}
