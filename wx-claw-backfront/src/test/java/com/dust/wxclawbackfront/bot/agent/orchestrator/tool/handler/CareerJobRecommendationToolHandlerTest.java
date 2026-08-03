package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerQueryNormalizer;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerTaskService;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerJobRecommendationToolHandlerTest {
    @Test
    void returnsRejectedSubmissionAsUserMessage() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerJobRecommendationToolHandler handler = new CareerJobRecommendationToolHandler(careerTools, mock(CareerQueryNormalizer.class));
        when(careerTools.recommendJobsByResume(
                List.of(), List.of(), List.of(), List.of(), null, null, null))
                .thenReturn(new CareerTaskService.TaskSubmission(false, null, false,
                        "请先发送 PDF 简历，再进行个性化岗位推荐。"));

        TaskResult result = handler.execute(TaskStep.builder().build(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).contains("请先发送 PDF 简历");
    }

    @Test
    void mapsPlannerParametersAndDelegatesRecommendation() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerJobRecommendationToolHandler handler = new CareerJobRecommendationToolHandler(careerTools, mock(CareerQueryNormalizer.class));
        TaskStep step = TaskStep.builder().params(Map.of(
                "locations", List.of("杭州"),
                "include_keywords", List.of("Java", "后端"),
                "exclude_keywords", List.of("外包"),
                "employment_types", List.of("INTERNSHIP"),
                "published_within_days", 14,
                "min_match_score", 70,
                "top_n", 8)).build();
        when(careerTools.recommendJobsByResume(
                List.of("杭州"), List.of("Java", "后端"), List.of("外包"),
                List.of("INTERNSHIP"), 14, 70, 8))
                .thenReturn(new CareerTaskService.TaskSubmission(true, "task-2", false, "推荐任务已开始"));

        TaskResult result = handler.execute(step, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).isEqualTo("推荐任务已开始");
        verify(careerTools).recommendJobsByResume(
                List.of("杭州"), List.of("Java", "后端"), List.of("外包"),
                List.of("INTERNSHIP"), 14, 70, 8);
    }


    @Test
    void extractsMissingParamsFromInputClause() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerQueryNormalizer normalizer = mock(CareerQueryNormalizer.class);
        CareerJobRecommendationToolHandler handler = new CareerJobRecommendationToolHandler(careerTools, normalizer);
        TaskStep step = TaskStep.builder().params(Map.of(
                "input", "根据我的简历推荐杭州的Java实习岗位")).build();
        when(normalizer.normalize("根据我的简历推荐杭州的Java实习岗位",
                new CareerQueryNormalizer.NormalizedQuery(List.of(), List.of(), List.of(), List.of(), null)))
                .thenReturn(new CareerQueryNormalizer.NormalizedQuery(List.of("杭州"), List.of("Java"),
                        List.of(), List.of("INTERNSHIP"), null));
        when(careerTools.recommendJobsByResume(
                List.of("杭州"), List.of("Java"), List.of(), List.of("INTERNSHIP"), null, null, null))
                .thenReturn(new CareerTaskService.TaskSubmission(true, "task-3", false, "推荐任务已开始"));

        TaskResult result = handler.execute(step, null);

        assertThat(result.isSuccess()).isTrue();
        verify(careerTools).recommendJobsByResume(
                List.of("杭州"), List.of("Java"), List.of(), List.of("INTERNSHIP"), null, null, null);
    }

    @Test
    void keepsStructuredParamsWhenInputAlsoPresent() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerQueryNormalizer normalizer = mock(CareerQueryNormalizer.class);
        CareerJobRecommendationToolHandler handler = new CareerJobRecommendationToolHandler(careerTools, normalizer);
        TaskStep step = TaskStep.builder().params(Map.of(
                "input", "根据我的简历推荐杭州的Java实习岗位",
                "locations", List.of("杭州"),
                "include_keywords", List.of("Java"),
                "employment_types", List.of("INTERNSHIP"))).build();
        when(careerTools.recommendJobsByResume(
                List.of("杭州"), List.of("Java"), List.of(), List.of("INTERNSHIP"), null, null, null))
                .thenReturn(new CareerTaskService.TaskSubmission(true, "task-4", false, "推荐任务已开始"));

        handler.execute(step, null);

        verify(careerTools).recommendJobsByResume(
                List.of("杭州"), List.of("Java"), List.of(), List.of("INTERNSHIP"), null, null, null);
        verify(normalizer, org.mockito.Mockito.never()).normalize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
