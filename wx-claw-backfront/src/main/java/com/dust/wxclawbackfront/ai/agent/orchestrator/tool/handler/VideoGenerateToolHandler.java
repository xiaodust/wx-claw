package com.dust.wxclawbackfront.ai.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.ai.agent.model.AgentContext;
import com.dust.wxclawbackfront.ai.agent.model.TaskResult;
import com.dust.wxclawbackfront.ai.agent.model.TaskStep;
import com.dust.wxclawbackfront.ai.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.ai.video.VideoGenerationHandler;
import com.dust.wxclawbackfront.ai.video.VideoGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 视频生成工具处理器
 * 当 Agent 规划了 video_generate 动作时执行
 */
@Slf4j
@Component
public class VideoGenerateToolHandler implements ToolHandler {

    private static final String MEDIA_TYPE = "video/mp4";
    private static final String FILE_NAME = "generated_video.mp4";

    private final VideoGenerationHandler videoGenerationHandler;

    public VideoGenerateToolHandler(VideoGenerationHandler videoGenerationHandler) {
        this.videoGenerationHandler = videoGenerationHandler;
    }

    @Override
    public String getName() {
        return "video_generate";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        Instant start = Instant.now();

        try {
            String prompt = getParam(step, "prompt");
            String ratio = getParam(step, "ratio");
            String durationStr = getParam(step, "duration");
            String resolution = getParam(step, "resolution");

            log.info("执行视频生成: prompt={}", prompt);

            if (prompt == null || prompt.isBlank()) {
                long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
                return TaskResult.failure("需要提供视频描述才能生成视频哦~", ms);
            }

            if (!videoGenerationHandler.isEnabled()) {
                long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
                return TaskResult.failure("视频生成功能未配置，请联系管理员。", ms);
            }

            Integer duration = null;
            if (durationStr != null && !durationStr.isBlank()) {
                try {
                    duration = Integer.parseInt(durationStr.trim());
                } catch (NumberFormatException e) {
                    log.warn("无效的视频时长参数: {}", durationStr);
                }
            }

            VideoGenerationResult result = videoGenerationHandler.generateFromText(
                    prompt.trim(), ratio, duration, resolution);

            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (result != null && result.isSuccess()) {
                byte[] videoBytes = result.getVideoBytes();
                if (videoBytes != null && videoBytes.length > 0) {
                    return TaskResult.successWithMedia("视频已生成", videoBytes, MEDIA_TYPE, FILE_NAME, executionTimeMs);
                }
                // 有URL但没下载到字节，返回文本提示
                if (result.getVideoUrl() != null) {
                    return TaskResult.success("视频已生成，链接: " + result.getVideoUrl(), executionTimeMs);
                }
            }

            String error = (result != null && result.getErrorMsg() != null) ? result.getErrorMsg() : "未知错误";
            log.error("视频生成失败: {}", error);
            return TaskResult.failure("视频生成失败: " + error, executionTimeMs);

        } catch (Exception e) {
            long executionTimeMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("视频生成异常: {}", e.getMessage(), e);
            return TaskResult.failure("视频生成异常: " + e.getMessage(), executionTimeMs);
        }
    }

    private String getParam(TaskStep step, String key) {
        if (step.getParams() != null) {
            Object value = step.getParams().get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }
}
