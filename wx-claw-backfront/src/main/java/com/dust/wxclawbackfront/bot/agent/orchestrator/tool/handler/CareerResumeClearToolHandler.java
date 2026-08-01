package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerResumeClearToolHandler implements ToolHandler {
    private final CareerTools careerTools;

    @Override public String getName() { return "career_resume_clear"; }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        CareerTools.ClearResumeResult result = careerTools.clearResume();
        return TaskResult.success(result.message(), Instant.now().toEpochMilli() - started);
    }
}
