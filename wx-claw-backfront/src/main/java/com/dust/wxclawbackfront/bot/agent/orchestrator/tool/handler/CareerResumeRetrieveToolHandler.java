package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerResumeRetrieveToolHandler implements ToolHandler {
    private final CareerResumeContextStore resumeStore;

    @Override public String getName() { return "career_resume_retrieve"; }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        return resumeStore.getCurrent()
                .map(resume -> TaskResult.successWithMedia("这是你当前保存的简历：", resume.fileBytes(),
                        "application/pdf", resume.fileName(), Instant.now().toEpochMilli() - started))
                .orElseGet(() -> TaskResult.failure("当前没有保存的简历。", Instant.now().toEpochMilli() - started));
    }
}
