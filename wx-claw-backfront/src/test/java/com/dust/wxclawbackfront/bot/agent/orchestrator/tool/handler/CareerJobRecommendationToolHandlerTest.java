package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
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
    void mapsPlannerParametersAndDelegatesRecommendation() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerJobRecommendationToolHandler handler = new CareerJobRecommendationToolHandler(careerTools);
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
}
