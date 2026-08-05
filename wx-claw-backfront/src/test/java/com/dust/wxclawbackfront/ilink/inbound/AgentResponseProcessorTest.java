package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.model.MediaAttachment;
import com.dust.wxclawbackfront.bot.agent.orchestrator.AgentOrchestrator;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.bot.service.ConversationSummaryService;
import com.dust.wxclawbackfront.bot.service.MemoryChunkService;
import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResponseProcessorTest {
    @Test
    void sendsEachMultiMediaAttachmentAndReturnsText() throws Exception {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        UserMemoryService memory = mock(UserMemoryService.class);
        ILinkMessageSender sender = mock(ILinkMessageSender.class);
        AgentResponseProcessor processor = new AgentResponseProcessor(orchestrator, memory, sender,
                mock(ConversationSummaryService.class), mock(MemoryChunkService.class));
        byte[] imageBytes = {1, 2};
        byte[] audioBytes = {3, 4};
        when(memory.getProfiles("user")).thenReturn(List.of());
        when(orchestrator.orchestrate(any(), any())).thenReturn(AgentResult.successWithMedia("故事+job",
                List.of(new MediaAttachment("image/png", "cat.png", imageBytes),
                        new MediaAttachment("audio/wav", "morning.wav", audioBytes))));

        String response = processor.process(ILinkUserInput.text("讲故事和画图"), List.of(), "user", "session");

        assertThat(response).isEqualTo("故事+job");
        verify(sender).sendImage("user", imageBytes, "cat.png", null);
        verify(sender).sendFile("user", audioBytes, "morning.wav", null);
    }

    @Test
    void sendsNonMediaMimeTypeAsFile() throws Exception {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        UserMemoryService memory = mock(UserMemoryService.class);
        ILinkMessageSender sender = mock(ILinkMessageSender.class);
        AgentResponseProcessor processor = new AgentResponseProcessor(orchestrator, memory, sender,
                mock(ConversationSummaryService.class), mock(MemoryChunkService.class));
        byte[] bytes = {1, 2, 3};
        when(memory.getProfiles("user")).thenReturn(List.of());
        when(orchestrator.orchestrate(any(), any())).thenReturn(
                AgentResult.successWithMedia("这是你的简历", bytes, "application/pdf", "resume.pdf"));

        String response = processor.process(ILinkUserInput.text("发送我的简历"), List.of(), "user", "session");

        assertThat(response).isNull();
        verify(sender).sendFile("user", bytes, "resume.pdf", "这是你的简历");
    }
}
