package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.knowledge.KnowledgeFileStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KnowledgeFileRetrieveToolHandler implements ToolHandler {
    private final KnowledgeFileStore fileStore;

    @Override public String getName() { return "knowledge_file_retrieve"; }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        long started = Instant.now().toEpochMilli();
        String label = step.getParams() == null ? "file" : String.valueOf(step.getParams().getOrDefault("label", "file"));
        return fileStore.find(label)
                .map(file -> TaskResult.successWithMedia("已取回知识库中的原始文件：" + file.fileName(), file.bytes(),
                        "application/octet-stream", file.fileName(), Instant.now().toEpochMilli() - started))
                .orElseGet(() -> TaskResult.failure("没有找到标签为“" + label + "”的文件。", Instant.now().toEpochMilli() - started));
    }
}
