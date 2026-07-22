package com.dust.wxclawbackfront.bot.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.bot.agent.llm.image.ImageGenerationHandler;
import com.dust.wxclawbackfront.bot.agent.llm.image.ImageGenerationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 图片生成工具处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenerateToolHandler implements ToolHandler {

    private final ImageGenerationHandler imageGenerationHandler;

    @Override
    public String getName() {
        return "image_generate";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        Instant start = Instant.now();

        try {
            String prompt = getPrompt(step, context);

            if (prompt == null || prompt.isBlank()) {
                long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
                return TaskResult.failure("缺少图片描述", ms);
            }

            ImageGenerationResult result = imageGenerationHandler.generate(prompt);

            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (result == null) {
                return TaskResult.failure("图片生成返回空结果", executionTimeMs);
            }

            String errorMsg = result.getErrorMsg();
            if (errorMsg != null && !errorMsg.isBlank()) {
                return TaskResult.failure("图片生成失败: " + errorMsg, executionTimeMs);
            }

            byte[] imageBytes = result.getImageBytes();
            if (imageBytes == null || imageBytes.length == 0) {
                return TaskResult.failure("生成的图片数据为空", executionTimeMs);
            }

            return TaskResult.successWithMedia(
                    "已生成图片",
                    imageBytes,
                    result.getContentType(),
                    result.getFileName(),
                    executionTimeMs);

        } catch (Exception e) {
            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("图片生成异常: {}", e.getMessage());
            return TaskResult.failure("图片生成异常: " + e.getMessage(), executionTimeMs);
        }
    }

    private String getPrompt(TaskStep step, AgentContext context) {
        if (step.getParams() != null) {
            Object prompt = step.getParams().get("prompt");
            if (prompt instanceof String && !((String) prompt).isBlank()) {
                return ((String) prompt).trim();
            }
        }
        return null;
    }
}
