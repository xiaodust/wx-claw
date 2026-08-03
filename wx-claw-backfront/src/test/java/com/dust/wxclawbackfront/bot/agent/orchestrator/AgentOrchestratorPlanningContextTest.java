package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.dust.wxclawbackfront.bot.agent.prompt.PromptLoader;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorPlanningContextTest {
    @Test
    void distinguishesGeneralJobSearchFromResumeRecommendation() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        PlanValidator validator = mock(PlanValidator.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(planningModel, taskExecutor, new ObjectMapper(), validator, new PromptLoader(true));
        AgentContext context = AgentContext.builder().userText("推荐一些腾讯开发岗").build();
        when(planningModel.chat(org.mockito.ArgumentMatchers.anyString(), eq("PLAN")))
                .thenReturn("{\"steps\":[{\"step\":1,\"tool\":\"career_job_search\",\"params\":{\"include_keywords\":[\"腾讯\",\"开发\"]}}]}");
        when(validator.validate(org.mockito.ArgumentMatchers.anyString())).thenReturn(PlanValidator.ValidationResult.valid());
        when(taskExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(context)))
                .thenReturn(List.of(com.dust.wxclawbackfront.bot.agent.model.TaskResult.success("ok", 1)));

        assertThat(orchestrator.orchestrate("推荐一些腾讯开发岗", context).isSuccess()).isTrue();
    }

    @Test
    void includesConversationHistoryWhenPlanningFollowUp() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        PlanValidator validator = mock(PlanValidator.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), validator, new PromptLoader(true));

        AiMessage previousRequest = new AiMessage();
        previousRequest.setMessageSeq(1);
        previousRequest.setMessageType(0);
        previousRequest.setContent("根据我的简历推荐杭州的岗位");
        AgentContext context = AgentContext.builder()
                .userText("扩大到全国")
                .historyMessages(List.of(previousRequest))
                .build();

        when(planningModel.chat(org.mockito.ArgumentMatchers.anyString(), eq("PLAN")))
                .thenAnswer(invocation -> {
                    String prompt = invocation.getArgument(0);
                    assertThat(prompt).contains("根据我的简历推荐杭州的岗位");
                    assertThat(prompt).contains("扩大到全国");
                    return "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"test\"}]}";
                });
        when(validator.validate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(PlanValidator.ValidationResult.valid());
        when(taskExecutor.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.same(context)))
                .thenReturn(List.of(com.dust.wxclawbackfront.bot.agent.model.TaskResult.success("ok", 1)));

        assertThat(orchestrator.orchestrate("扩大到全国", context).isSuccess()).isTrue();
    }
}
