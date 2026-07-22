package com.dust.wxclawbackfront.bot.agent.llm.image;

import com.dust.wxclawbackfront.bot.agent.tools.shared.TextSanitizer;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class ImageGenerationHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String url;
    private final Duration timeout;
    private final String generationModel;
    private final String imageSize;
    private final int numInferenceSteps;
    private final double guidanceScale;
    private final String replyText;

    public ImageGenerationHandler(ObjectMapper objectMapper,
                                  @Value("${wxclaw.ai.image.generate.api-key:${spring.ai.openai.api-key:}}") String apiKey,
                                  @Value("${wxclaw.ai.image.generate.url:https://api.siliconflow.cn/v1/images/generations}") String url,
                                  @Value("${wxclaw.ai.image.generate.timeout:PT35S}") Duration timeout,
                                  @Value("${wxclaw.ai.image.generate.model:Kwai-Kolors/Kolors}") String generationModel,
                                  @Value("${wxclaw.ai.image.generate.image-size:1024x1024}") String imageSize,
                                  @Value("${wxclaw.ai.image.generate.num-inference-steps:20}") int numInferenceSteps,
                                  @Value("${wxclaw.ai.image.generate.guidance-scale:7.5}") double guidanceScale,
                                  @Value("${wxclaw.ai.image.generate.reply-text:已根据你的描述生成了一张图片，请查收。}") String replyText) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey;
        this.url = url;
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
        String model = generationModel == null ? null : generationModel.trim();
        if (model == null || model.isBlank()) {
            return new ImageGenerationResult(null, null, null, null, null, null, null, null, "未配置生图 model");
        }
        String normalizedPrompt = prompt.trim();

        String actualUrl = url == null ? null : url.trim();
        if (actualUrl == null || actualUrl.isBlank()) {
            actualUrl = "https://api.siliconflow.cn/v1/images/generations";
        }
        String key = apiKey == null ? null : apiKey.trim();
        if (key == null || key.isBlank()) {
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, "未配置生图 API Key");
        }

        try {
            return generateWithSiliconFlow(actualUrl, key, model, normalizedPrompt);
        } catch (Exception ex) {
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, ex.getMessage());
        }
    }

    private ImageGenerationResult generateWithSiliconFlow(String url, String apiKey, String model, String prompt) throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("image_size", imageSize);
        requestBody.put("batch_size", 1);
        requestBody.put("num_inference_steps", numInferenceSteps);
        requestBody.put("guidance_scale", guidanceScale);

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

        String imageUrl = parseImageUrl(responseText);
        if (imageUrl == null || imageUrl.isBlank()) {
            return new ImageGenerationResult(model, requestJson, responseJson, null, null, null, null, null,
                    "生图响应格式错误，原始响应: " + (responseText != null && responseText.length() > 200 ? responseText.substring(0, 200) + "..." : responseText));
        }

        byte[] imageBytes = downloadImage(imageUrl);
        if (imageBytes == null || imageBytes.length == 0) {
            return new ImageGenerationResult(model, requestJson, responseJson, null, null, null, null, null,
                    "下载图片失败");
        }

        String fileName = "generated-" + Instant.now().toEpochMilli() + ".png";
        String contentType = "image/png";

        return new ImageGenerationResult(model, requestJson, responseJson, prompt, imageUrl, imageBytes, fileName, contentType, null);
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

    @SuppressWarnings("unchecked")
    private String parseImageUrl(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return null;
            }

            // 尝试解析 images 数组
            Object imagesObj = responseMap.get("images");
            if (imagesObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> imagesList = (java.util.List<Map<String, Object>>) imagesObj;
                if (!imagesList.isEmpty()) {
                    Map<String, Object> firstImage = imagesList.get(0);
                    if (firstImage != null) {
                        Object urlObj = firstImage.get("url");
                        if (urlObj instanceof String) {
                            return (String) urlObj;
                        }
                    }
                }
            }

            // 尝试解析 data 数组（兼容其他格式）
            Object dataObj = responseMap.get("data");
            if (dataObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> dataList = (java.util.List<Map<String, Object>>) dataObj;
                if (!dataList.isEmpty()) {
                    Map<String, Object> firstData = dataList.get(0);
                    if (firstData != null) {
                        Object urlObj = firstData.get("url");
                        if (urlObj instanceof String) {
                            return (String) urlObj;
                        }
                    }
                }
            }

            return null;
        } catch (Exception ex) {
            return null;
        }
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
}
