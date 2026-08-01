package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerQueryNormalizer;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerReplyFormatter;
import com.dust.wxclawbackfront.bot.agent.career.service.CareerSearchContextStore;
import com.dust.wxclawbackfront.bot.agent.career.tools.CareerTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerJobSearchToolHandlerTest {
    @Test
    void carriesFiltersAndAdvancesPageForMore() {
        CareerTools tools = mock(CareerTools.class);
        CareerQueryNormalizer normalizer = mock(CareerQueryNormalizer.class);
        CareerSearchContextStore store = new CareerSearchContextStore();
        CareerJobSearchToolHandler handler = new CareerJobSearchToolHandler(tools, normalizer, store);
        CareerQueryNormalizer.NormalizedQuery query = new CareerQueryNormalizer.NormalizedQuery(
                List.of("杭州"), List.of("Java"), List.of(), List.of("INTERNSHIP"), null);
        when(normalizer.normalize(eq("杭州 Java 实习"), any())).thenReturn(query);
        when(tools.searchJobs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CareerReplyFormatter.SearchToolResult(true, "r", 1, true, null, List.of(), null))
                .thenReturn(new CareerReplyFormatter.SearchToolResult(true, "r2", 2, false, null, List.of(), null));

        handler.execute(TaskStep.builder().params(Map.of()).build(), AgentContext.builder()
                .userId("u1").userText("杭州 Java 实习").build());
        handler.execute(TaskStep.builder().params(Map.of()).build(), AgentContext.builder()
                .userId("u1").userText("更多").build());

        verify(tools).searchJobs(eq(List.of("杭州")), eq(List.of("Java")), eq(List.of()),
                eq(List.of("INTERNSHIP")), eq(null), eq(2), eq(null));
    }
}
