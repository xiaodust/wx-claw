package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.bot.agent.llm.chat.ChatHandler;
import com.dust.wxclawbackfront.bot.agent.llm.chat.file.FileContentExtractor;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerResumeAnalyzeToolHandlerTest {
    @Test
    void readsStoredResumeBeforeCallingChatModel() {
        CareerResumeContextStore store = mock(CareerResumeContextStore.class);
        FileContentExtractor extractor = mock(FileContentExtractor.class);
        ChatHandler chat = mock(ChatHandler.class);
        byte[] pdf = "%PDF-resume".getBytes();
        when(store.getCurrent()).thenReturn(Optional.of(
                new CareerResumeContextStore.PendingResume("resume.pdf", pdf, "hash")));
        when(extractor.extractComplete(pdf, "resume.pdf"))
                .thenReturn(FileContentExtractor.FileExtractResult.success("Java developer", ".pdf"));
        when(chat.chatWithDocument("根据简历写自我介绍", "resume.pdf", "Java developer", List.of()))
                .thenReturn("自我介绍");
        CareerResumeAnalyzeToolHandler handler = new CareerResumeAnalyzeToolHandler(store, extractor, chat);

        TaskResult result = handler.execute(TaskStep.builder().build(),
                AgentContext.builder().userText("根据简历写自我介绍").historyMessages(List.of()).build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTextResult()).isEqualTo("自我介绍");
        verify(chat).chatWithDocument("根据简历写自我介绍", "resume.pdf", "Java developer", List.of());
    }
}
