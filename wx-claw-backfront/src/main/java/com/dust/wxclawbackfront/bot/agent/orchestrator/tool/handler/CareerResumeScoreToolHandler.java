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
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerResumeScoreToolHandler implements ToolHandler {
    private final CareerTools careerTools;

    @Override
    public String getName() {
        return "career_resume_score";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        Map<String, Object> params = step.getParams() == null ? Map.of() : step.getParams();
        String jobDescription = stringParam(params, "job_description", "jobDescription", "jd");
        CareerTaskService.TaskSubmission submission = careerTools.scoreResume(jobDescription);
        long duration = Instant.now().toEpochMilli() - started;
        return TaskResult.success(submission.message(), duration);
    }

    private String stringParam(Map<String, Object> params, String... names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return null;
    }
}
