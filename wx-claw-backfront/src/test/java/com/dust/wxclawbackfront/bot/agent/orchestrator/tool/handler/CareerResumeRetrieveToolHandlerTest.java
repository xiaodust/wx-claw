package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerResumeRetrieveToolHandlerTest {
    @Test
    void returnsUserPromptWhenResumeIsMissing() {
        CareerResumeContextStore store = mock(CareerResumeContextStore.class);
        when(store.getCurrent()).thenReturn(Optional.empty());
        CareerResumeRetrieveToolHandler handler = new CareerResumeRetrieveToolHandler(store);

        TaskResult result = handler.execute(TaskStep.builder().build(), AgentContext.builder().build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).contains("请先发送 PDF 简历");
    }
}
