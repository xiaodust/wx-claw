package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskPlan;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.dust.wxclawbackfront.bot.agent.prompt.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorDirectChatTest {

    private AgentOrchestrator orchestrator(PlainTextLlmService planningModel, TaskExecutor taskExecutor,
                                           PlanValidator validator) {
        return new AgentOrchestrator(planningModel, taskExecutor, new ObjectMapper(), validator, new PromptLoader(true));
    }

    @Test
    void executesChatWithoutCallingPlanningModel() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = orchestrator(planningModel, taskExecutor, mock(PlanValidator.class));
        AgentContext context = AgentContext.builder().userText("立即分析媒体").build();
        when(taskExecutor.execute(any(), same(context))).thenReturn(List.of(TaskResult.success("已处理", 1)));

        AgentResult result = orchestrator.orchestrateChat(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReplyText()).isEqualTo("已处理");
        verify(planningModel, never()).chat(any(), any());
    }

    @Test
    void fallbackPlanAlwaysReturnsChat() throws Exception {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = orchestrator(planningModel, taskExecutor, mock(PlanValidator.class));

        var fallback = AgentOrchestrator.class.getDeclaredMethod("fallbackPlan", String.class);
        fallback.setAccessible(true);
        TaskPlan plan = (TaskPlan) fallback.invoke(orchestrator, "给我发个早上问候语音");

        assertThat(plan.getSteps()).hasSize(1);
        assertThat(plan.getSteps().get(0).getToolName()).isEqualTo("chat");
    }

    @Test
    void ordinaryMessageGoesThroughPlanningModel() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        PlanValidator validator = mock(PlanValidator.class);
        when(validator.validate(any())).thenReturn(PlanValidator.ValidationResult.valid());
        AgentOrchestrator orchestrator = orchestrator(planningModel, taskExecutor, validator);
        AgentContext context = AgentContext.builder().userText("杭州今天天气怎么样").build();
        when(planningModel.chat(any(), eq("PLAN")))
                .thenReturn("{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"天气查询\"}]}");
        when(taskExecutor.execute(any(), same(context))).thenReturn(List.of(TaskResult.success("晴", 1)));

        AgentResult result = orchestrator.orchestrate("杭州今天天气怎么样", context);

        assertThat(result.getReplyText()).isEqualTo("晴");
        verify(planningModel).chat(any(), eq("PLAN"));
    }
}
