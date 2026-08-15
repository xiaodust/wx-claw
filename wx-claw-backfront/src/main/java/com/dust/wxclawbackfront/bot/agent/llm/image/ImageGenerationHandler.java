package com.dust.wxclawbackfront.bot.agent.llm.image;

import com.dust.wxclawbackfront.bot.agent.llm.TenantAiKeyProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.observability.llm.service.LlmInvocationRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片生成：按用户所选服务商分发。
 *
 * <p>支持 SiliconFlow（Kolors/FLUX/SD3.5）、火山方舟（Doubao-Seedream）、OpenAI（gpt-image/dall-e），
 * 统一走 OpenAI 兼容的 /images/generations 接口，差异只在请求体参数与响应解析。</p>
 */
@Slf4j
@Service
public class ImageGenerationHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TenantAiKeyProvider keyProvider;
    private final Duration timeout;
    private final String generationModel;
    private final String imageSize;
    private final int numInferenceSteps;
    private final double guidanceScale;
    private final String replyText;
    private final LlmInvocationRecorder invocationRecorder;

    public ImageGenerationHandler(ObjectMapper objectMapper,
                                  LlmInvocationRecorder invocationRecorder,
                                  TenantAiKeyProvider keyProvider,
                                  @Value("${wxclaw.ai.image.generate.timeout:PT35S}") Duration timeout,
                                  @Value("${wxclaw.ai.image.generate.model:Kwai-Kolors/Kolors}") String generationModel,
                                  @Value("${wxclaw.ai.image.generate.image-size:1024x1024}") String imageSize,
                                  @Value("${wxclaw.ai.image.generate.num-inference-steps:20}") int numInferenceSteps,
                                  @Value("${wxclaw.ai.image.generate.guidance-scale:7.5}") double guidanceScale,
                                  @Value("${wxclaw.ai.image.generate.reply-text:已根据你的描述生成了一张图片，请查收。}") String replyText) {
        this.objectMapper = objectMapper;
        this.invocationRecorder = invocationRecorder;
        this.keyProvider = keyProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.timeout = timeout == null ? Duration.ofSeconds(35) : timeout;
        this.generationModel = generationModel;
        this.imageSize = imageSize;
        this.numInferenceSteps = numInferenceSteps;
        this.guidanceScale = guidanceScale;
        this.replyText = replyText;
    }

    public ImageGenerationResult generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, "生图提示词为空");
        }
        String provider = keyProvider.imageProvider() == null ? null : keyProvider.imageProvider().trim();
        String model = keyProvider.imageModel() == null ? null : keyProvider.imageModel().trim();
        if (model == null || model.isBlank()) {
            return new ImageGenerationResult(null, null, null, null, null, null, null, null, "未配置生图 model");
        }
        String normalizedPrompt = prompt.trim();

        String baseUrl = keyProvider.imageBaseUrlFor(provider);
        String actualUrl = (baseUrl == null ? "" : baseUrl.trim())
                + (baseUrl == null || baseUrl.isBlank() || baseUrl.endsWith("/")
                ? "" : "/") + "images/generations";
        String key = keyProvider.imageKey() == null ? null : keyProvider.imageKey().trim();
        if (key == null || key.isBlank()) {
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, "图片生成未配置 API Key，请在用户控制台「设置」页配置");
        }

        Map<String, Object> auditRequest = new LinkedHashMap<>();
        auditRequest.put("model", model);
        auditRequest.put("provider", provider);
        auditRequest.put("prompt", normalizedPrompt);
        auditRequest.put("image_size", imageSize);
        auditRequest.put("batch_size", 1);
        auditRequest.put("num_inference_steps", numInferenceSteps);
        auditRequest.put("guidance_scale", guidanceScale);
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "IMAGE_GENERATION", provider == null ? "UNKNOWN" : provider.toUpperCase(), model, toJson(auditRequest));
        try {
            ImageGenerationResult result = generateWithProvider(actualUrl, key, provider, model, normalizedPrompt);
            if (result.getErrorMsg() == null) {
                invocationRecorder.success(handle, result.getResponseJson(), null, null, null);
            } else {
                invocationRecorder.failure(handle, new IllegalStateException(result.getErrorMsg()), result.getResponseJson());
            }
            return result;
        } catch (Exception ex) {
            invocationRecorder.failure(handle, ex);
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, ex.getMessage());
        }
    }

    private ImageGenerationResult generateWithProvider(String url, String apiKey, String provider,
                                                       String model, String prompt) throws Exception {
        Map<String, Object> requestBody = buildRequestBody(provider, model, prompt);
        if (requestBody == null) {
            return new ImageGenerationResult(model, null, null, null, null, null, null, null,
                    "不支持的图片生成服务商: " + provider);
        }

        requestBody.put("model", model);
        requestBody.put("prompt", prompt);

        String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);

        long start = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        log.info("图片生成请求完成, 耗时={}ms, model={}", elapsed, model);
        String responseText = response.body();
        String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(responseText));

        if (response.statusCode() / 100 != 2) {
            return new ImageGenerationResult(model, requestJson, responseJson, null, null, null, null, null,
                    "生图请求失败，HTTP " + response.statusCode());
        }

        ImageData imageData = parseImageData(responseText);
        if (imageData == null || (imageData.url() == null && imageData.base64() == null)) {
            return new ImageGenerationResult(model, requestJson, responseJson, null, null, null, null, null,
                    "生图响应格式错误，原始响应: " + (responseText != null && responseText.length() > 200 ? responseText.substring(0, 200) + "..." : responseText));
        }

        byte[] imageBytes = imageData.base64() != null
                ? decodeBase64(imageData.base64())
                : downloadImage(imageData.url());
        if (imageBytes == null || imageBytes.length == 0) {
            return new ImageGenerationResult(model, requestJson, responseJson, null, null, null, null, null,
                    "下载图片失败");
        }

        String fileName = "generated-" + Instant.now().toEpochMilli() + ".png";
        String contentType = "image/png";

        return new ImageGenerationResult(model, requestJson, responseJson, prompt, imageData.url(), imageBytes, fileName, contentType, null);
    }

    private Map<String, Object> buildRequestBody(String provider, String model, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        if ("siliconflow".equalsIgnoreCase(provider)) {
            body.put("image_size", imageSize);
            body.put("batch_size", 1);
            body.put("num_inference_steps", numInferenceSteps);
            body.put("guidance_scale", guidanceScale);
            return body;
        }
        if ("ark".equalsIgnoreCase(provider)) {
            body.put("size", imageSize);
            body.put("response_format", "url");
            return body;
        }
        if ("openai".equalsIgnoreCase(provider)) {
            body.put("size", imageSize);
            body.put("n", 1);
            body.put("response_format", "b64_json");
            return body;
        }
        return null;
    }

    private byte[] decodeBase64(String base64) {
        try {
            String data = base64.trim();
            int comma = data.indexOf(',');
            if (comma >= 0 && data.startsWith("data:")) {
                data = data.substring(comma + 1);
            }
            return Base64.getDecoder().decode(data);
        } catch (Exception ex) {
            log.warn("base64 图片解码失败: {}", ex.getMessage());
            return null;
        }
    }

    private byte[] downloadImage(String imageUrl) {
        int maxRetries = 2;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() / 100 == 2) {
                    return response.body();
                }
                // 如果失败，等待后重试
                if (i < maxRetries) {
                    Thread.sleep(1000 * (i + 1));
                }
            } catch (Exception ex) {
                if (i < maxRetries) {
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        return null;
    }

    private record ImageData(String url, String base64) {
    }

    @SuppressWarnings("unchecked")
    private ImageData parseImageData(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return null;
            }

            // 尝试解析 images 数组（SiliconFlow 格式）
            Object imagesObj = responseMap.get("images");
            if (imagesObj instanceof java.util.List && !((java.util.List<?>) imagesObj).isEmpty()) {
                Map<String, Object> firstImage = (Map<String, Object>) ((java.util.List<?>) imagesObj).get(0);
                ImageData data = extractImageData(firstImage);
                if (data != null) {
                    return data;
                }
            }

            // 尝试解析 data 数组（Ark/OpenAI 兼容格式）
            Object dataObj = responseMap.get("data");
            if (dataObj instanceof java.util.List && !((java.util.List<?>) dataObj).isEmpty()) {
                Map<String, Object> firstData = (Map<String, Object>) ((java.util.List<?>) dataObj).get(0);
                ImageData data = extractImageData(firstData);
                if (data != null) {
                    return data;
                }
            }

            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private ImageData extractImageData(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        Object urlObj = item.get("url");
        if (urlObj instanceof String url && !url.isBlank()) {
            return new ImageData(url, null);
        }
        Object b64Obj = item.get("b64_json");
        if (b64Obj instanceof String b64 && !b64.isBlank()) {
            return new ImageData(null, b64);
        }
        return null;
    }

    public String getReplyText() {
        return replyText;
    }

    private String toPrettyJsonOrRaw(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            Object any = objectMapper.readValue(text, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ignore) {
            return text;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
