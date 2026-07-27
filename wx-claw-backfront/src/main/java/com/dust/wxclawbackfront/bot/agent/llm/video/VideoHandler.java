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
 * 视频理解服务
 * 调用火山方舟 Chat Completions API，通过 video_url 内容类型实现视频理解
 */
@Slf4j
@Service
public class VideoHandler {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String videoModel;
    private final String defaultModel;
    private final String prompt;
    private final float fps;
    private final int maxTokens;
    private final Duration timeout;
    private final LlmInvocationRecorder invocationRecorder;

    public VideoHandler(
            ObjectMapper objectMapper,
            LlmInvocationRecorder invocationRecorder,
            @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${wxclaw.ai.video.model:}") String videoModel,
            @Value("${spring.ai.openai.chat.model:}") String defaultModel,
            @Value("${wxclaw.ai.video.prompt:请用中文描述这个视频的内容，尽量提取关键信息与可用于对话的细节。}") String prompt,
            @Value("${wxclaw.ai.video.fps:1.0}") float fps,
            @Value("${wxclaw.ai.video.max-tokens:1024}") int maxTokens,
            @Value("${wxclaw.ai.video.timeout:PT60S}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.invocationRecorder = invocationRecorder;
        this.apiUrl = baseUrl + "/chat/completions";
        this.apiKey = apiKey;
        this.videoModel = videoModel;
        this.defaultModel = defaultModel;
        this.prompt = prompt;
        this.fps = fps;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public VideoUnderstandingResult understandByUrl(String videoUrl) {
        return understandByUrl(videoUrl, null);
    }

    public VideoUnderstandingResult understandByUrl(String videoUrl, String userText) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return new VideoUnderstandingResult(null, null, null, "videoUrl is blank");
        }

        String modelToUse = (videoModel == null || videoModel.isBlank()) ? defaultModel : videoModel;
        if (modelToUse == null || modelToUse.isBlank()) {
            return new VideoUnderstandingResult(null, null, null, "video model is not configured");
        }

        String promptToUse;
        if (userText == null || userText.isBlank()) {
            promptToUse = prompt;
        } else {
            promptToUse = "请用中文先描述视频内容，然后结合用户问题给出关键信息。\n用户问题：\n" + userText.trim();
        }

        String requestJson = buildRequestJson(modelToUse, videoUrl, promptToUse);
        return executeUnderstanding(requestJson, modelToUse);
    }

    public VideoUnderstandingResult understandByBase64(String base64Data, String mimeType) {
        if (base64Data == null || base64Data.isBlank()) {
            return new VideoUnderstandingResult(null, null, null, "base64Data is blank");
        }

        String modelToUse = (videoModel == null || videoModel.isBlank()) ? defaultModel : videoModel;
        if (modelToUse == null || modelToUse.isBlank()) {
            return new VideoUnderstandingResult(null, null, null, "video model is not configured");
        }

        String dataUrl = "data:" + (mimeType != null ? mimeType : "video/mp4") + ";base64," + base64Data;
        String requestJson = buildRequestJson(modelToUse, dataUrl, prompt);
        return executeUnderstanding(requestJson, modelToUse);
    }

    private VideoUnderstandingResult executeUnderstanding(String requestJson, String model) {
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "VIDEO_UNDERSTANDING", "VOLCENGINE", model, requestJson);
        try {
            Map<String, Object> requestBody = objectMapper.readValue(requestJson, Map.class);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("视频理解完成, 耗时={}ms, model={}", System.currentTimeMillis() - start, model);
            if (response.statusCode() / 100 != 2) {
                String error = "API返回HTTP " + response.statusCode();
                invocationRecorder.failure(handle, new IllegalStateException(error), response.body());
                return new VideoUnderstandingResult(model, requestJson, null, error);
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    String content = message.get("content").asText();
                    invocationRecorder.success(handle, response.body(), null, null, null);
                    return new VideoUnderstandingResult(model, requestJson, content, null);
                }
            }
            String error = "无法解析API响应";
            invocationRecorder.failure(handle, new IllegalStateException(error), response.body());
            return new VideoUnderstandingResult(model, requestJson, null, error);
        } catch (Exception ex) {
            log.error("视频理解异常: {}", ex.getMessage(), ex);
            invocationRecorder.failure(handle, ex);
            return new VideoUnderstandingResult(model, requestJson, null, ex.getMessage());
        }
    }

    private String buildRequestJson(String model, String videoUrl, String promptText) {
        try {
            Map<String, Object> videoUrlObj = new LinkedHashMap<>();
            videoUrlObj.put("url", videoUrl);
            videoUrlObj.put("fps", fps);

            Map<String, Object> videoContent = new LinkedHashMap<>();
            videoContent.put("type", "video_url");
            videoContent.put("video_url", videoUrlObj);

            Map<String, Object> textContent = new LinkedHashMap<>();
            textContent.put("type", "text");
            textContent.put("text", promptText);

            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", List.of(videoContent, textContent));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", List.of(userMessage));
            if (maxTokens > 0) {
                payload.put("max_tokens", maxTokens);
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean isEnabled() {
        String modelToUse = (videoModel == null || videoModel.isBlank()) ? defaultModel : videoModel;
        return modelToUse != null && !modelToUse.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
