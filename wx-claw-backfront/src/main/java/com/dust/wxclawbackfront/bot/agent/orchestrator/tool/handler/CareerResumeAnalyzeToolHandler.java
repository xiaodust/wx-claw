package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.career.context.CareerResumeContextStore;
import com.dust.wxclawbackfront.bot.agent.llm.chat.ChatHandler;
import com.dust.wxclawbackfront.bot.agent.llm.chat.file.FileContentExtractor;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class CareerResumeAnalyzeToolHandler implements ToolHandler {
    private final CareerResumeContextStore resumeStore;
    private final FileContentExtractor extractor;
    private final ChatHandler chatHandler;

    @Override public String getName() { return "career_resume_analyze"; }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        return resumeStore.getCurrent().map(resume -> {
            FileContentExtractor.FileExtractResult extracted = extractor.extractComplete(
                    resume.fileBytes(), resume.fileName());
            if (!extracted.isSuccess()) {
                return TaskResult.failure("无法读取已保存简历：" + extracted.error(), elapsed(started));
            }
            String reply = chatHandler.chatWithDocument(context.getUserText(), resume.fileName(), extracted.content(),
                    context.getHistoryMessages() == null ? List.of() : context.getHistoryMessages());
            return reply == null || reply.isBlank()
                    ? TaskResult.failure("基于简历生成的结果为空", elapsed(started))
                    : TaskResult.success(reply, elapsed(started));
        }).orElseGet(() -> TaskResult.success("当前没有保存的简历，请先发送 PDF 简历。", elapsed(started)));
    }

    private long elapsed(long started) { return Instant.now().toEpochMilli() - started; }
}
