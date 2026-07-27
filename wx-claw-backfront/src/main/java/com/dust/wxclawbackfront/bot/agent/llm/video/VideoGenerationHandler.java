package com.dust.wxclawbackfront.bot.agent.llm.video;

import com.dust.wxclawbackfront.observability.llm.service.LlmInvocationRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频生成服务
 * 支持火山方舟 Seedance 和阿里云通义万相（DashScope）双平台
 * 通过 wxclaw.ai.video-gen.provider 切换：ark / dashscope
 */
@Slf4j
@Service
public class VideoGenerationHandler {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 通用配置
    private final String provider;
    private final String ratio;
    private final int duration;
    private final String resolution;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;

    // 火山方舟配置
    private final String arkBaseUrl;
    private final String arkApiKey;
    private final String arkModel;

    // 阿里云 DashScope 配置
    private final String dashscopeApiKey;
    private final String dashscopeT2vModel;
    private final String dashscopeI2vModel;
    private final LlmInvocationRecorder invocationRecorder;

    public VideoGenerationHandler(
            ObjectMapper objectMapper,
            LlmInvocationRecorder invocationRecorder,
            @Value("${wxclaw.ai.video-gen.provider:ark}") String provider,
            @Value("${wxclaw.ai.video-gen.ratio:16:9}") String ratio,
            @Value("${wxclaw.ai.video-gen.duration:5}") int duration,
            @Value("${wxclaw.ai.video-gen.resolution:720p}") String resolution,
            @Value("${wxclaw.ai.video-gen.poll-interval-ms:5000}") long pollIntervalMs,
            @Value("${wxclaw.ai.video-gen.poll-timeout-ms:300000}") long pollTimeoutMs,
            // 火山方舟
            @Value("${wxclaw.ai.video-gen.ark.base-url:https://ark.cn-beijing.volces.com/api/v3}") String arkBaseUrl,
            @Value("${wxclaw.ai.video-gen.ark.api-key:${spring.ai.openai.api-key:}}") String arkApiKey,
            @Value("${wxclaw.ai.video-gen.ark.model:doubao-seedance-2-0-mini-260615}") String arkModel,
            // 阿里云 DashScope
            @Value("${wxclaw.ai.video-gen.dashscope.api-key:}") String dashscopeApiKey,
            @Value("${wxclaw.ai.video-gen.dashscope.t2v-model:wan2.7-t2v-2026-06-12}") String dashscopeT2vModel,
            @Value("${wxclaw.ai.video-gen.dashscope.i2v-model:wan2.7-i2v-2026-04-25}") String dashscopeI2vModel) {
        this.objectMapper = objectMapper;
        this.invocationRecorder = invocationRecorder;
        this.provider = provider;
        this.ratio = ratio;
        this.duration = duration;
        this.resolution = resolution;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
        this.arkBaseUrl = arkBaseUrl;
        this.arkApiKey = arkApiKey;
        this.arkModel = arkModel;
        this.dashscopeApiKey = dashscopeApiKey;
        this.dashscopeT2vModel = dashscopeT2vModel;
        this.dashscopeI2vModel = dashscopeI2vModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        log.info("视频生成服务初始化: provider={}, arkModel={}, dashscopeT2v={}, dashscopeI2v={}",
                provider, arkModel, dashscopeT2vModel, dashscopeI2vModel);
    }

    // ==================== 公开接口 ====================

    /**
     * 文生视频
     */
    public VideoGenerationResult generateFromText(String prompt) {
        return generateFromText(prompt, null, null, null);
    }

    public VideoGenerationResult generateFromText(String prompt, String customRatio, Integer customDuration, String customResolution) {
        if (prompt == null || prompt.isBlank()) {
            return VideoGenerationResult.failure("prompt is blank");
        }

        String ratioToUse = customRatio != null ? customRatio : ratio;
        int durationToUse = customDuration != null ? customDuration : duration;
        String resolutionToUse = customResolution != null ? customResolution : resolution;
        String model = "dashscope".equalsIgnoreCase(provider) ? dashscopeT2vModel : arkModel;
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "VIDEO_GENERATION", provider, model,
                auditRequest("text", prompt, null, ratioToUse, durationToUse, resolutionToUse));

        try {
            long start = System.currentTimeMillis();
            String taskId = createTextToVideoTask(prompt, ratioToUse, durationToUse, resolutionToUse);
            if (taskId == null) {
                return complete(handle, VideoGenerationResult.failure("创建视频生成任务失败"));
            }
            log.info("文生视频任务已创建: provider={}, taskId={}", provider, taskId);
            VideoGenerationResult result = pollTask(taskId);
            log.info("视频生成完成, 耗时={}ms, provider={}", System.currentTimeMillis() - start, provider);
            return complete(handle, result);
        } catch (RuntimeException ex) {
            invocationRecorder.failure(handle, ex);
            throw ex;
        }
    }

    /**
     * 图生视频
     */
    public VideoGenerationResult generateFromImage(String imageUrl, String prompt) {
        return generateFromImage(imageUrl, prompt, null, null, null);
    }

    public VideoGenerationResult generateFromImage(String imageUrl, String prompt, String customRatio, Integer customDuration, String customResolution) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return VideoGenerationResult.failure("imageUrl is blank");
        }

        String ratioToUse = customRatio != null ? customRatio : ratio;
        int durationToUse = customDuration != null ? customDuration : duration;
        String resolutionToUse = customResolution != null ? customResolution : resolution;
        String model = "dashscope".equalsIgnoreCase(provider) ? dashscopeI2vModel : arkModel;
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "VIDEO_GENERATION", provider, model,
                auditRequest("image", prompt, imageUrl, ratioToUse, durationToUse, resolutionToUse));

        try {
            long start = System.currentTimeMillis();
            String taskId = createImageToVideoTask(imageUrl, prompt, ratioToUse, durationToUse, resolutionToUse);
            if (taskId == null) {
                return complete(handle, VideoGenerationResult.failure("创建视频生成任务失败"));
            }
            log.info("图生视频任务已创建: provider={}, taskId={}", provider, taskId);
            VideoGenerationResult result = pollTask(taskId);
            log.info("视频生成完成, 耗时={}ms, provider={}", System.currentTimeMillis() - start, provider);
            return complete(handle, result);
        } catch (RuntimeException ex) {
            invocationRecorder.failure(handle, ex);
            throw ex;
        }
    }

    private VideoGenerationResult complete(LlmInvocationRecorder.InvocationHandle handle,
                                           VideoGenerationResult result) {
        String response = toJson(Map.of(
                "success", result.isSuccess(),
                "videoUrl", result.getVideoUrl() == null ? "" : result.getVideoUrl(),
                "videoBytesLength", result.getVideoBytes() == null ? 0 : result.getVideoBytes().length,
                "error", result.getErrorMsg() == null ? "" : result.getErrorMsg()));
        if (result.isSuccess()) {
            invocationRecorder.success(handle, response, null, null, null);
        } else {
            invocationRecorder.failure(handle, new IllegalStateException(result.getErrorMsg()), response);
        }
        return result;
    }

    private String auditRequest(String mode, String prompt, String imageUrl, String ratio,
                                int duration, String resolution) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("mode", mode);
        request.put("prompt", prompt);
        request.put("imageUrl", imageUrl);
        request.put("ratio", ratio);
        request.put("duration", duration);
        request.put("resolution", resolution);
        return toJson(request);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    public boolean isEnabled() {
        if ("dashscope".equals(provider)) {
            return dashscopeApiKey != null && !dashscopeApiKey.isBlank();
        }
        // 默认 ark
        return arkApiKey != null && !arkApiKey.isBlank();
    }

    // ==================== 任务创建（按 provider 分发） ====================

    private String createTextToVideoTask(String prompt, String ratioVal, int durationVal, String resolutionVal) {
        if ("dashscope".equals(provider)) {
            return createDashScopeTask(prompt, null, ratioVal, durationVal, resolutionVal);
        }
        return createArkTask(prompt, null, ratioVal, durationVal, resolutionVal);
    }

    private String createImageToVideoTask(String imageUrl, String prompt, String ratioVal, int durationVal, String resolutionVal) {
        if ("dashscope".equals(provider)) {
            return createDashScopeTask(prompt, imageUrl, ratioVal, durationVal, resolutionVal);
        }
        return createArkTask(prompt, imageUrl, ratioVal, durationVal, resolutionVal);
    }

    // ==================== 火山方舟 Seedance ====================

    private String createArkTask(String prompt, String imageUrl, String ratioVal, int durationVal, String resolutionVal) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", arkModel);

            List<Map<String, Object>> content;
            if (imageUrl != null && !imageUrl.isBlank()) {
                Map<String, Object> imageContent = new LinkedHashMap<>();
                imageContent.put("type", "image_url");
                imageContent.put("image_url", Map.of("url", imageUrl));
                imageContent.put("role", "first_frame");

                if (prompt != null && !prompt.isBlank()) {
                    content = List.of(imageContent, Map.of("type", "text", "text", prompt));
                } else {
                    content = List.of(imageContent);
                }
            } else {
                content = List.of(Map.of("type", "text", "text", prompt));
            }

            payload.put("content", content);
            payload.put("ratio", ratioVal);
            payload.put("duration", durationVal);
            payload.put("resolution", resolutionVal);

            String body = objectMapper.writeValueAsString(payload);
            log.info("[Ark] 创建视频生成任务: model={}, prompt={}", arkModel, truncate(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(arkBaseUrl + "/contents/generations/tasks"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + arkApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("[Ark] 创建视频任务失败: HTTP {}, body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode idNode = root.get("id");
            if (idNode != null) {
                return idNode.asText();
            }

            log.error("[Ark] 创建视频任务响应缺少id: {}", response.body());
            return null;

        } catch (Exception ex) {
            log.error("[Ark] 创建视频任务异常: {}", ex.getMessage(), ex);
            return null;
        }
    }

    // ==================== 阿里云 DashScope 通义万相 ====================

    private String createDashScopeTask(String prompt, String imageUrl, String ratioVal, int durationVal, String resolutionVal) {
        try {
            // 选择模型：图生视频用 i2v，文生视频用 t2v
            boolean isImageToVideo = imageUrl != null && !imageUrl.isBlank();
            String model = isImageToVideo ? dashscopeI2vModel : dashscopeT2vModel;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);

            // input
            Map<String, Object> input = new LinkedHashMap<>();
            if (prompt != null && !prompt.isBlank()) {
                input.put("prompt", prompt);
            }
            if (isImageToVideo) {
                input.put("img_url", imageUrl);
            }
            payload.put("input", input);

            // parameters
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("resolution", resolutionVal.toUpperCase()); // DashScope 用大写：720P
            parameters.put("ratio", ratioVal);
            parameters.put("duration", durationVal);
            payload.put("parameters", parameters);

            String body = objectMapper.writeValueAsString(payload);
            log.info("[DashScope] 创建视频生成任务: model={}, t2v={}, i2v={}, prompt={}",
                    model, dashscopeT2vModel, dashscopeI2vModel, truncate(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + dashscopeApiKey)
                    .header("X-DashScope-Async", "enable")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.error("[DashScope] 创建视频任务失败: HTTP {}, body={}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            // DashScope 返回 output.task_id
            JsonNode taskIdNode = root.at("/output/task_id");
            if (taskIdNode != null && !taskIdNode.isMissingNode()) {
                return taskIdNode.asText();
            }

            log.error("[DashScope] 创建视频任务响应缺少task_id: {}", response.body());
            return null;

        } catch (Exception ex) {
            log.error("[DashScope] 创建视频任务异常: {}", ex.getMessage(), ex);
            return null;
        }
    }

    // ==================== 轮询任务状态（按 provider 分发） ====================

    private VideoGenerationResult pollTask(String taskId) {
        if ("dashscope".equals(provider)) {
            return pollDashScopeTask(taskId);
        }
        return pollArkTask(taskId);
    }

    private VideoGenerationResult pollArkTask(String taskId) {
        String queryUrl = arkBaseUrl + "/contents/generations/tasks/" + taskId;
        long deadline = System.currentTimeMillis() + pollTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(pollIntervalMs);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(queryUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + arkApiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    log.error("[Ark] 查询视频任务失败: HTTP {}", response.statusCode());
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.body());
                String status = root.has("status") ? root.get("status").asText() : "unknown";

                if ("succeeded".equals(status)) {
                    log.info("[Ark] 视频生成成功: taskId={}", taskId);
                    return extractArkVideoResult(root);
                } else if ("failed".equals(status) || "cancelled".equals(status)) {
                    String error = root.has("error") ? root.get("error").toString() : "任务" + status;
                    log.error("[Ark] 视频生成失败: taskId={}, error={}", taskId, error);
                    return VideoGenerationResult.failure("视频生成失败: " + error);
                }

                log.debug("[Ark] 视频生成中: taskId={}, status={}", taskId, status);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return VideoGenerationResult.failure("轮询被中断");
            } catch (Exception ex) {
                log.warn("[Ark] 轮询视频任务异常: {}", ex.getMessage());
            }
        }

        return VideoGenerationResult.failure("视频生成超时（" + (pollTimeoutMs / 1000) + "秒）");
    }

    private VideoGenerationResult pollDashScopeTask(String taskId) {
        String queryUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
        long deadline = System.currentTimeMillis() + pollTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(pollIntervalMs);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(queryUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + dashscopeApiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 != 2) {
                    log.error("[DashScope] 查询视频任务失败: HTTP {}", response.statusCode());
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.body());
                String status = root.at("/output/task_status").asText("UNKNOWN");

                if ("SUCCEEDED".equals(status)) {
                    log.info("[DashScope] 视频生成成功: taskId={}", taskId);
                    return extractDashScopeVideoResult(root);
                } else if ("FAILED".equals(status)) {
                    String errorMsg = root.at("/output/message").asText("任务失败");
                    log.error("[DashScope] 视频生成失败: taskId={}, error={}", taskId, errorMsg);
                    return VideoGenerationResult.failure("视频生成失败: " + errorMsg);
                }

                log.debug("[DashScope] 视频生成中: taskId={}, status={}", taskId, status);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return VideoGenerationResult.failure("轮询被中断");
            } catch (Exception ex) {
                log.warn("[DashScope] 轮询视频任务异常: {}", ex.getMessage());
            }
        }

        return VideoGenerationResult.failure("视频生成超时（" + (pollTimeoutMs / 1000) + "秒）");
    }

    // ==================== 结果提取 ====================

    /**
     * 火山方舟结果提取：output[0].url 或 output.video_url
     */
    private VideoGenerationResult extractArkVideoResult(JsonNode root) {
        try {
            JsonNode output = root.get("output");
            if (output != null && output.isArray() && !output.isEmpty()) {
                JsonNode firstOutput = output.get(0);
                String videoUrl = firstOutput.has("url") ? firstOutput.get("url").asText() : null;

                if (videoUrl != null && !videoUrl.isBlank()) {
                    return downloadOrReturnUrl(videoUrl);
                }
            }

            JsonNode videoUrlNode = root.at("/output/video_url");
            if (videoUrlNode != null && !videoUrlNode.isMissingNode()) {
                return downloadOrReturnUrl(videoUrlNode.asText());
            }

            return VideoGenerationResult.failure("无法从结果中提取视频URL: " + root);

        } catch (Exception ex) {
            log.error("[Ark] 提取视频结果异常: {}", ex.getMessage(), ex);
            return VideoGenerationResult.failure("提取视频结果失败: " + ex.getMessage());
        }
    }

    /**
     * 阿里云 DashScope 结果提取：output.video_url
     */
    private VideoGenerationResult extractDashScopeVideoResult(JsonNode root) {
        try {
            String videoUrl = root.at("/output/video_url").asText(null);

            if (videoUrl != null && !videoUrl.isBlank()) {
                return downloadOrReturnUrl(videoUrl);
            }

            return VideoGenerationResult.failure("无法从DashScope结果中提取视频URL: " + root);

        } catch (Exception ex) {
            log.error("[DashScope] 提取视频结果异常: {}", ex.getMessage(), ex);
            return VideoGenerationResult.failure("提取视频结果失败: " + ex.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private VideoGenerationResult downloadOrReturnUrl(String videoUrl) {
        log.info("下载生成的视频: {}", videoUrl);
        byte[] videoBytes = downloadVideo(videoUrl);
        if (videoBytes != null && videoBytes.length > 0) {
            return VideoGenerationResult.success(videoBytes, videoUrl);
        }
        return VideoGenerationResult.successWithUrl(videoUrl);
    }

    private byte[] downloadVideo(String videoUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(videoUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() / 100 == 2 && response.body().length > 0) {
                log.info("视频下载成功, size={}", response.body().length);
                return response.body();
            }

            log.error("视频下载失败: HTTP {}", response.statusCode());
            return null;

        } catch (Exception ex) {
            log.error("视频下载异常: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private static String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
