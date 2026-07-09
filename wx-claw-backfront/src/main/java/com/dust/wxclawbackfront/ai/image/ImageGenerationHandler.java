package com.dust.wxclawbackfront.ai.image;

import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class ImageGenerationHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String url;
    private final Duration timeout;
    private final boolean wrapRequest;
    private final String generationModel;
    private final String size;
    private final String responseFormat;
    private final boolean watermark;
    private final String sequentialImageGeneration;
    private final String outputFormat;
    private final String replyText;

    public ImageGenerationHandler(ObjectMapper objectMapper,
                                  @Value("${spring.ai.openai.api-key:}") String apiKey,
                                  @Value("${wxclaw.ai.image.generate.url:${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}/images/generations}") String url,
                                  @Value("${wxclaw.ai.image.generate.timeout:PT60S}") Duration timeout,
                                  @Value("${wxclaw.ai.image.generate.wrap-request:false}") boolean wrapRequest,
                                  @Value("${wxclaw.ai.image.generate.model:}") String generationModel,
                                  @Value("${wxclaw.ai.image.generate.size:2K}") String size,
                                  @Value("${wxclaw.ai.image.generate.response-format:b64_json}") String responseFormat,
                                  @Value("${wxclaw.ai.image.generate.watermark:true}") boolean watermark,
                                  @Value("${wxclaw.ai.image.generate.sequential-image-generation:disabled}") String sequentialImageGeneration,
                                  @Value("${wxclaw.ai.image.generate.output-format:png}") String outputFormat,
                                  @Value("${wxclaw.ai.image.generate.reply-text:已根据你的描述生成了一张图片，请查收。}") String replyText) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.apiKey = apiKey;
        this.url = url;
        this.timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.wrapRequest = wrapRequest;
        this.generationModel = generationModel;
        this.size = size;
        this.responseFormat = responseFormat;
        this.watermark = watermark;
        this.sequentialImageGeneration = sequentialImageGeneration;
        this.outputFormat = outputFormat;
        this.replyText = replyText;
    }

    public ImageGenerationResult generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new ImageGenerationResult(generationModel, null, null, null, null, null, null, null, "生图提示词为空");
        }
        String model = generationModel == null ? null : generationModel.trim();
        if (model == null || model.isBlank()) {
            return new ImageGenerationResult(null, null, null, null, null, null, null, null, "未配置生图 model（例如: doubao-seedream-5-0-260128）");
        }
        String normalizedPrompt = prompt.trim();

        SeedreamRequestEnvelope envelope = buildRequestEnvelope(model, normalizedPrompt);
        String requestJson = toPrettyJsonOrNull(envelope.payload());

        String actualUrl = url == null ? null : url.trim();
        if (actualUrl == null || actualUrl.isBlank()) {
            return new ImageGenerationResult(generationModel, requestJson, null, null, null, null, null, null, "未配置生图 URL");
        }
        String key = apiKey == null ? null : apiKey.trim();
        if (key == null || key.isBlank()) {
            return new ImageGenerationResult(generationModel, requestJson, null, null, null, null, null, null, "未配置生图 API Key");
        }

        try {
            HttpResponse<String> response = httpClient.send(buildHttpRequest(actualUrl, key, envelope), HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();
            String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(responseText));
            if (response.statusCode() / 100 != 2) {
                return new ImageGenerationResult(generationModel, requestJson, responseJson, null, null, null, null, null, "生图请求失败，HTTP " + response.statusCode());
            }

            SeedreamResponseBody parsed = parseResponse(responseText);
            if (parsed == null || parsed.firstB64() == null || parsed.firstB64().isBlank()) {
                return new ImageGenerationResult(generationModel, requestJson, responseJson, null, null, null, null, null, "生图响应缺少 b64_json");
            }

            String b64 = parsed.firstB64().trim();
            byte[] bytes = Base64.getDecoder().decode(b64);
            String ext = outputFormat == null || outputFormat.isBlank() ? "png" : outputFormat.trim().toLowerCase();
            String contentType = ("jpg".equals(ext) || "jpeg".equals(ext)) ? "image/jpeg" : ("webp".equals(ext) ? "image/webp" : "image/png");
            String fileName = "generated-" + Instant.now().toEpochMilli() + "." + ext;
            String imageUrl = "data:" + contentType + ";base64," + b64;

            return new ImageGenerationResult(
                    generationModel,
                    requestJson,
                    responseJson,
                    normalizedPrompt,
                    TextSanitizer.summarizeDataUrl(imageUrl),
                    bytes,
                    fileName,
                    contentType,
                    null
            );
        } catch (Exception ex) {
            return new ImageGenerationResult(generationModel, requestJson, null, null, null, null, null, null, ex.getMessage());
        }
    }

    public String getReplyText() {
        return replyText;
    }

    private HttpRequest buildHttpRequest(String url, String apiKey, SeedreamRequestEnvelope envelope) throws Exception {
        Object httpPayload;
        if (wrapRequest) {
            httpPayload = envelope.payload();
        } else {
            httpPayload = envelope.payload().get("body");
        }
        String payload = objectMapper.writeValueAsString(httpPayload);
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }

    private SeedreamRequestEnvelope buildRequestEnvelope(String model, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("response_format", responseFormat);
        body.put("watermark", watermark);
        body.put("sequential_image_generation", sequentialImageGeneration);
        body.put("size", size);

        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("body", body);
        return new SeedreamRequestEnvelope(payload);
    }

    private SeedreamResponseBody parseResponse(String responseText) {
        try {
            return objectMapper.readValue(responseText, SeedreamResponseBody.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toPrettyJsonOrNull(Object any) {
        if (any == null) {
            return null;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ex) {
            return null;
        }
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

    private record SeedreamRequestEnvelope(Map<String, Object> payload) {
    }

    private static class SeedreamResponseBody {
        public String model;
        public Long created;
        public SeedreamData[] data;
        public SeedreamUsage usage;

        public String firstB64() {
            if (data == null || data.length == 0 || data[0] == null) {
                return null;
            }
            return data[0].b64_json;
        }
    }

    private static class SeedreamData {
        public String b64_json;
        public String size;
    }

    private static class SeedreamUsage {
        public Integer generated_images;
        public Integer output_tokens;
        public Integer total_tokens;
    }
}
