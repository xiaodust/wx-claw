package com.dust.wxclawbackfront.ai.agent.orchestrator.tool.handler;

import com.dust.wxclawbackfront.ai.agent.model.AgentContext;
import com.dust.wxclawbackfront.ai.agent.model.TaskResult;
import com.dust.wxclawbackfront.ai.agent.model.TaskStep;
import com.dust.wxclawbackfront.ai.agent.orchestrator.tool.ToolHandler;
import com.dust.wxclawbackfront.ai.video.VideoGenerationHandler;
import com.dust.wxclawbackfront.ai.video.VideoGenerationResult;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频生成工具处理器（异步模式）
 * 启动后台线程执行视频生成，立即返回"生成中"提示
 * 视频生成完毕后自动发送给用户，期间不影响其他消息处理
 */
@Slf4j
@Component
public class VideoGenerateToolHandler implements ToolHandler {

    private static final String FILE_NAME = "generated_video.mp4";

    private static final ExecutorService VIDEO_EXECUTOR = Executors.newFixedThreadPool(3, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "video-gen-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final VideoGenerationHandler videoGenerationHandler;
    private final ILinkMessageSender messageSender;

    public VideoGenerateToolHandler(VideoGenerationHandler videoGenerationHandler,
                                     ILinkMessageSender messageSender) {
        this.videoGenerationHandler = videoGenerationHandler;
        this.messageSender = messageSender;
    }

    @Override
    public String getName() {
        return "video_generate";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        String prompt = getParam(step, "prompt");
        String ratio = getParam(step, "ratio");
        String durationStr = getParam(step, "duration");
        String resolution = getParam(step, "resolution");
        String userId = context.getUserId();

        log.info("执行视频生成(异步): prompt={}, userId={}", prompt, userId);

        if (prompt == null || prompt.isBlank()) {
            return TaskResult.failure("需要提供视频描述才能生成视频哦~", 0);
        }

        if (!videoGenerationHandler.isEnabled()) {
            return TaskResult.failure("视频生成功能未配置，请联系管理员。", 0);
        }

        Integer duration = null;
        if (durationStr != null && !durationStr.isBlank()) {
            try {
                duration = Integer.parseInt(durationStr.trim());
            } catch (NumberFormatException e) {
                log.warn("无效的视频时长参数: {}", durationStr);
            }
        }

        // 异步启动视频生成，不阻塞当前线程
        final String finalPrompt = prompt.trim();
        final Integer finalDuration = duration;

        CompletableFuture.runAsync(() -> {
            long asyncStart = System.currentTimeMillis();
            try {
                log.info("[异步] 视频生成开始: userId={}, prompt={}", userId, truncate(finalPrompt));

                VideoGenerationResult result = videoGenerationHandler.generateFromText(
                        finalPrompt, ratio, finalDuration, resolution);

                long elapsed = System.currentTimeMillis() - asyncStart;

                if (result != null && result.isSuccess()) {
                    byte[] videoBytes = result.getVideoBytes();
                    if (videoBytes != null && videoBytes.length > 0) {
                        messageSender.sendVideo(userId, videoBytes, FILE_NAME, null, "视频已生成，请查收~");
                        log.info("[异步] 视频已发送: userId={}, size={}, 耗时={}ms", userId, videoBytes.length, elapsed);
                    } else if (result.getVideoUrl() != null) {
                        messageSender.sendText(userId, "视频已生成，链接: " + result.getVideoUrl());
                        log.info("[异步] 视频链接已发送: userId={}, 耗时={}ms", userId, elapsed);
                    } else {
                        messageSender.sendText(userId, "视频生成完成但无法获取结果，请稍后重试。");
                    }
                } else {
                    String error = (result != null && result.getErrorMsg() != null) ? result.getErrorMsg() : "未知错误";
                    log.error("[异步] 视频生成失败: userId={}, error={}", userId, error);
                    messageSender.sendText(userId, "视频生成失败: " + error);
                }
            } catch (Exception e) {
                log.error("[异步] 视频生成异常: userId={}, error={}", userId, e.getMessage(), e);
                try {
                    messageSender.sendText(userId, "视频生成失败，请稍后再试。");
                } catch (Exception ignored) {
                }
            }
        }, VIDEO_EXECUTOR);

        // 立即返回，不阻塞
        return TaskResult.success("视频正在生成中，请稍候，生成完毕后会自动发送给你~", 0);
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

    private static String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
