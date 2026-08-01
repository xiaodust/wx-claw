package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorDirectChatTest {
    @Test
    void executesChatWithoutCallingPlanningModel() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), mock(PlanValidator.class));
        AgentContext context = AgentContext.builder().userText("立即分析媒体").build();
        when(taskExecutor.execute(any(), same(context))).thenReturn(List.of(TaskResult.success("已处理", 1)));

        AgentResult result = orchestrator.orchestrateChat(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReplyText()).isEqualTo("已处理");
        verify(planningModel, never()).chat(any(), any());
    }

    @Test
    void ordinaryMessageSkipsPlanningAutomatically() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), mock(PlanValidator.class));
        AgentContext context = AgentContext.builder().userText("杭州今天天气怎么样").build();
        when(taskExecutor.execute(any(), same(context))).thenReturn(List.of(TaskResult.success("晴", 1)));

        AgentResult result = orchestrator.orchestrate("杭州今天天气怎么样", context);

        assertThat(result.getReplyText()).isEqualTo("晴");
        verify(planningModel, never()).chat(any(), any());
    }
}
