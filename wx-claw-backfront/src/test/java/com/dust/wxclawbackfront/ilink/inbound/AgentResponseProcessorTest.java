package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.orchestrator.AgentOrchestrator;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
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
    void sendsNonMediaMimeTypeAsFile() throws Exception {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        UserMemoryService memory = mock(UserMemoryService.class);
        ILinkMessageSender sender = mock(ILinkMessageSender.class);
        AgentResponseProcessor processor = new AgentResponseProcessor(orchestrator, memory, sender);
        byte[] bytes = {1, 2, 3};
        when(memory.getProfiles("user")).thenReturn(List.of());
        when(orchestrator.orchestrate(any(), any())).thenReturn(
                AgentResult.successWithMedia("这是你的简历", bytes, "application/pdf", "resume.pdf"));

        String response = processor.process(ILinkUserInput.text("发送我的简历"), List.of(), "user", "session");

        assertThat(response).isNull();
        verify(sender).sendFile("user", bytes, "resume.pdf", "这是你的简历");
    }
}
