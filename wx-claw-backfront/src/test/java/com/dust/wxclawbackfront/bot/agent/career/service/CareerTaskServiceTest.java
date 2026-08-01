package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.dust.wxclawbackfront.bot.service.AiConversationCrudService;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextExecutorService;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.TenantContextTaskDecorator;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContext;
import com.dust.wxclawbackfront.observability.llm.InvocationTraceContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerTaskServiceTest {
    private ExecutorService delegate;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        InvocationTraceContextHolder.clear();
        if (delegate != null) {
            delegate.shutdownNow();
        }
    }

    @Test
    void scoresAsynchronouslyAndPushesUsingCapturedTenantContext() throws Exception {
        JobHelperProperties properties = new JobHelperProperties();
        properties.setEnabled(true);
        JobHelperMcpClient client = mock(JobHelperMcpClient.class);
        CareerResumeContextStore resumeStore = new CareerResumeContextStore(properties, client);
        TenantContextHolder.set(new TenantContext("tenant-a", "ILINK", "bot-a", "internal-a", "wx-a",
                Set.of(), Set.of(), "request-a"));
        InvocationTraceContextHolder.set(new InvocationTraceContext(
                "tenant-a", "bot-a", "conversation-a", "session-a", "trace-a"));
        assertTrue(resumeStore.storeCurrent("resume.pdf", "%PDF-test".getBytes()).stored());

        when(client.score(org.mockito.ArgumentMatchers.any(), eq(null))).thenReturn(
                new JobHelperDtos.ResumeScoreResponse("helper-request", new ObjectMapper().readTree("{\"score\":91}"), "raw"));
        ILinkMessageSender sender = mock(ILinkMessageSender.class);
        AiConversationCrudService conversationService = mock(AiConversationCrudService.class);
        delegate = Executors.newSingleThreadExecutor();
        ExecutorService decorated = new TenantContextExecutorService(delegate, new TenantContextTaskDecorator());
        CareerTaskService service = new CareerTaskService(client, resumeStore,
                new CareerReplyFormatter(properties), sender, conversationService, decorated);

        CareerTaskService.TaskSubmission submission = service.submitScore(null);

        assertTrue(submission.accepted());
        verify(sender, org.mockito.Mockito.timeout(2000)).sendText(eq("wx-a"), contains("91"));
        verify(conversationService, org.mockito.Mockito.timeout(2000))
                .appendMessage(eq("session-a"), eq(1), contains("91"), eq(null), eq(null), eq(null));
    }
}
