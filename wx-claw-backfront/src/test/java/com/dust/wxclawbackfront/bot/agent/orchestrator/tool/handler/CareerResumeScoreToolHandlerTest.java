package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerTaskService;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerResumeScoreToolHandlerTest {
    @Test
    void returnsRejectedSubmissionAsUserMessage() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerResumeScoreToolHandler handler = new CareerResumeScoreToolHandler(careerTools);
        when(careerTools.scoreResume(null))
                .thenReturn(new CareerTaskService.TaskSubmission(false, null, false,
                        "请先发送 PDF 简历，再进行简历评分。"));

        TaskResult result = handler.execute(TaskStep.builder().build(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).contains("请先发送 PDF 简历");
    }

    @Test
    void delegatesResumeScoreToCareerTools() {
        CareerTools careerTools = mock(CareerTools.class);
        CareerResumeScoreToolHandler handler = new CareerResumeScoreToolHandler(careerTools);
        TaskStep step = TaskStep.builder()
                .params(Map.of("job_description", "Java backend JD"))
                .build();
        when(careerTools.scoreResume("Java backend JD"))
                .thenReturn(new CareerTaskService.TaskSubmission(true, "task-1", false, "任务已开始"));

        TaskResult result = handler.execute(step, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).isEqualTo("任务已开始");
        verify(careerTools).scoreResume("Java backend JD");
    }
}
