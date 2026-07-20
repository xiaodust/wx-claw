package com.dust.wxclawbackfront.ai.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.ai.agent.model.AgentContext;
import com.dust.wxclawbackfront.ai.agent.model.TaskResult;
import com.dust.wxclawbackfront.ai.agent.model.TaskStep;
import com.dust.wxclawbackfront.ai.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.ai.chat.ChatHandler;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 对话工具处理器
 * 只负责普通对话、问答，模型自主决定调用哪些底层工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatToolHandler implements ToolHandler {

    private final ChatHandler chatHandler;

    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        Instant start = Instant.now();

        try {
            String userMessage = context.getUserText();
            List<AiMessage> historyMessages = context.getHistoryMessages() != null
                    ? context.getHistoryMessages() : Collections.emptyList();

            String reply = chatHandler.chat(userMessage, historyMessages);

            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (reply == null || reply.isBlank()) {
                return TaskResult.failure("对话返回为空", executionTimeMs);
            }

            return TaskResult.success(reply, executionTimeMs);

        } catch (Exception e) {
            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("对话执行失败: {}", e.getMessage());
            return TaskResult.failure("对话失败: " + e.getMessage(), executionTimeMs);
        }
    }
}
