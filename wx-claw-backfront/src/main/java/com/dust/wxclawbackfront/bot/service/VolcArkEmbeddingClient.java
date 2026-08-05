package com.dust.wxclawbackfront.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟多模态向量化模型（doubao-embedding-vision 系列）的纯 HTTP 客户端。
 *
 * <p>该模型必须走 {@code POST /embeddings/multimodal}（标准 {@code /embeddings} 会返回
 * HTTP 400）：请求体 input 为 {@code [{"type":"text","text":...}]}，响应中每个
 * {@code data[i].embedding} 是嵌套的二维数组。实测 doubao-embedding-vision-251215 对文本
 * 输入返回的是 {@code {"data":{"embedding":[...]}}}（扁平数组、一次请求一个向量），
 * 解析层同时兼容这两种外壳。这里直接对接接口，不依赖 Spring AI 的 {@code EmbeddingModel}
 * 抽象，调用方（如 {@link MemoryChunkService}）按需使用。</p>
 *
 * <p>失败策略：仅对网络层异常（含超时）重试，HTTP 错误直接抛出，由调用方统一降级跳过，
 * 不影响主链路。</p>
 */
@Slf4j
@Service
public class VolcArkEmbeddingClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final Duration timeout;
    private final int maxAttempts;

    public VolcArkEmbeddingClient(ObjectMapper objectMapper,
                                  @Value("${wxclaw.memory.vector.base-url:${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}}") String baseUrl,
                                  @Value("${wxclaw.memory.vector.api-key:${spring.ai.openai.api-key:}}") String apiKey,
                                  @Value("${wxclaw.memory.vector.model:doubao-embedding-vision-251215}") String model,
                                  @Value("${wxclaw.memory.vector.dimensions:1024}") int dimensions,
                                  @Value("${wxclaw.memory.vector.embedding-timeout:PT30S}") Duration timeout,
                                  @Value("${wxclaw.memory.vector.embedding-max-attempts:3}") int maxAttempts) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        String base = baseUrl == null || baseUrl.isBlank()
                ? "https://ark.cn-beijing.volces.com/api/v3"
                : baseUrl.trim().replaceAll("/+$", "");
        this.endpoint = base + "/embeddings/multimodal";
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    }

    /**
     * 将单条文本向量化，返回维度为 {@link #dimensions} 的稠密向量。
     *
     * <p>注意：该多模态端点按"一次请求一个向量"返回（不做逐条批量），因此需要逐条调用。
     * 空白文本直接拒绝，避免无意义请求。</p>
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("embedding 文本不能为空");
        }
        HttpResponse<String> response = execute(buildPayload(List.of(text)));
        return parseResponse(response.body()).getFirst();
    }

    public int dimensions() {
        return dimensions;
    }

    private String buildPayload(List<String> texts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("dimensions", dimensions);
        List<Map<String, String>> multimodalInput = new ArrayList<>(texts.size());
        for (String text : texts) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", "text");
            item.put("text", text == null ? "" : text);
            multimodalInput.add(item);
        }
        payload.put("input", multimodalInput);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("embedding 请求序列化失败", e);
        }
    }

    private HttpResponse<String> execute(String payloadJson) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("embedding 请求失败，HTTP " + response.statusCode()
                            + ": " + snippet(response.body()));
                }
                return response;
            } catch (IOException e) {
                lastFailure = e;
                log.warn("embedding 请求异常（第 {}/{} 次），将重试: {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("embedding 请求被中断", e);
            }
        }
        throw new IllegalStateException("embedding 请求最终失败: " + lastFailure.getMessage(), lastFailure);
    }

    private List<float[]> parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            List<float[]> embeddings = new ArrayList<>();
            if (data.isObject()) {
                // 实测 doubao-embedding-vision-251215：{"data":{"embedding":[...]}}
                addVectors(embeddings, data.path("embedding"));
            } else if (data.isArray()) {
                // 兼容文档示例：{"data":[{"embedding":[[...]]}, ...]}
                for (int i = 0; i < data.size(); i++) {
                    addVectors(embeddings, data.get(i).path("embedding"));
                }
            } else {
                throw new IllegalStateException("embedding 响应缺少 data 字段: " + snippet(body));
            }
            if (embeddings.isEmpty()) {
                throw new IllegalStateException("embedding 响应未包含有效向量: " + snippet(body));
            }
            return embeddings;
        } catch (IOException e) {
            throw new IllegalStateException("embedding 响应解析失败: " + snippet(body), e);
        }
    }

    private void addVectors(List<float[]> embeddings, JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("embedding 字段缺失或格式异常");
        }
        if (node.size() > 0 && node.get(0).isArray()) {
            // 多模态端点返回嵌套二维数组 [[...]]
            for (JsonNode vectorNode : node) {
                embeddings.add(parseVector(vectorNode));
            }
        } else {
            embeddings.add(parseVector(node));
        }
    }

    private float[] parseVector(JsonNode vectorNode) {
        float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            JsonNode value = vectorNode.get(i);
            if (!value.isNumber()) {
                throw new IllegalStateException("embedding 包含非数值元素");
            }
            vector[i] = (float) value.doubleValue();
        }
        if (vector.length != dimensions) {
            throw new IllegalStateException("embedding 维度与配置不符：期望 " + dimensions
                    + "，实际 " + vector.length);
        }
        return vector;
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "(空响应)";
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
