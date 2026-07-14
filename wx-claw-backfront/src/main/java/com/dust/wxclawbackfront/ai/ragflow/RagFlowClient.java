package com.dust.wxclawbackfront.ai.ragflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * RAGFlow 客户端
 * 提供知识库检索和对话功能
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wxclaw.ragflow.enabled", havingValue = "true")
public class RagFlowClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String chatId;
    private final String datasetId;
    private final Duration timeout;

    public RagFlowClient(ObjectMapper objectMapper,
                         @Value("${wxclaw.ragflow.base-url:http://localhost:9380}") String baseUrl,
                         @Value("${wxclaw.ragflow.api-key:}") String apiKey,
                         @Value("${wxclaw.ragflow.chat-id:}") String chatId,
                         @Value("${wxclaw.ragflow.dataset-id:}") String datasetId,
                         @Value("${wxclaw.ragflow.timeout:PT30S}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.chatId = chatId;
        this.datasetId = datasetId;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    /**
     * 向 RAGFlow 提问并获取回答
     *
     * @param question 用户问题
     * @return 回答结果
     */
    public RagFlowResult ask(String question) {
        if (chatId == null || chatId.isBlank()) {
            return new RagFlowResult(null, null, "未配置 RAGFlow chat-id");
        }

        try {
            // 使用新的 API 路径
            String url = baseUrl + "/api/v1/openai/" + chatId + "/chat/completions";

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "model");
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", question)
            ));
            requestBody.put("stream", false);
            // reference 需要放在 extra_body 中
            requestBody.put("extra_body", Map.of("reference", true));

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            log.info("RAGFlow 响应: HTTP {}, body={}", response.statusCode(), 
                    responseText != null && responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 请求失败: HTTP {}, body={}", response.statusCode(), responseText);
                return new RagFlowResult(null, null, "RAGFlow 请求失败: HTTP " + response.statusCode());
            }

            return parseResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 请求异常: {}", ex.getMessage(), ex);
            return new RagFlowResult(null, null, ex.getMessage());
        }
    }

    /**
     * 在知识库中搜索相关文档
     *
     * @param query 搜索关键词
     * @param topK  返回数量
     * @return 搜索结果
     */
    public List<SearchResult> search(String query, int topK) {
        if (datasetId == null || datasetId.isBlank()) {
            return List.of();
        }

        try {
            String url = baseUrl + "/api/v1/datasets/" + datasetId + "/search";

            Map<String, Object> requestBody = Map.of(
                    "question", query,
                    "top_k", topK
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 搜索失败: HTTP {}", response.statusCode());
                return List.of();
            }

            return parseSearchResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 搜索异常: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * 解析对话响应
     */
    @SuppressWarnings("unchecked")
    private RagFlowResult parseResponse(String responseText) {
        try {
            log.debug("RAGFlow 原始响应: {}", responseText);

            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return new RagFlowResult(null, null, "响应为空");
            }

            // 检查是否有错误响应
            if (responseMap.containsKey("code") && responseMap.containsKey("message")) {
                Object code = responseMap.get("code");
                String message = (String) responseMap.get("message");
                // 如果 code 不是 0 或 200，表示错误
                if (code instanceof Number && ((Number) code).intValue() != 0 && ((Number) code).intValue() != 200) {
                    log.error("RAGFlow 返回错误: code={}, message={}", code, message);
                    return new RagFlowResult(null, null, "RAGFlow 错误: " + message);
                }
            }

            // 提取回答内容 - 兼容 OpenAI 格式
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

                String content = message != null ? (String) message.get("content") : null;
                Object reference = message != null ? message.get("reference") : null;

                if (content != null && !content.isBlank()) {
                    return new RagFlowResult(content, reference, null);
                }
            }

            // 尝试直接从响应中提取 content（某些 RAGFlow 版本）
            Object directContent = responseMap.get("content");
            if (directContent instanceof String && !((String) directContent).isBlank()) {
                return new RagFlowResult((String) directContent, responseMap.get("reference"), null);
            }

            // 尝试从 data 中提取
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data != null) {
                Object dataContent = data.get("content");
                if (dataContent instanceof String && !((String) dataContent).isBlank()) {
                    return new RagFlowResult((String) dataContent, data.get("reference"), null);
                }
            }

            log.warn("RAGFlow 响应格式无法解析，keys: {}", responseMap.keySet());
            return new RagFlowResult(null, null, "无回答内容");

        } catch (Exception ex) {
            log.error("解析 RAGFlow 响应失败: {}", ex.getMessage());
            return new RagFlowResult(null, null, "解析响应失败");
        }
    }

    /**
     * 解析搜索响应
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> parseSearchResponse(String responseText) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return List.of();
            }

            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data == null) {
                return List.of();
            }

            List<Map<String, Object>> chunks = (List<Map<String, Object>>) data.get("chunks");
            if (chunks == null) {
                return List.of();
            }

            List<SearchResult> results = new ArrayList<>();
            for (Map<String, Object> chunk : chunks) {
                String content = (String) chunk.get("content");
                String documentName = (String) chunk.get("document_keyword");
                Double similarity = chunk.get("similarity") != null ?
                        ((Number) chunk.get("similarity")).doubleValue() : null;

                if (content != null && !content.isBlank()) {
                    results.add(new SearchResult(content, documentName, similarity));
                }
            }

            return results;

        } catch (Exception ex) {
            log.error("解析 RAGFlow 搜索响应失败: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 对话结果
     */
    public record RagFlowResult(String content, Object reference, String error) {
        public boolean isSuccess() {
            return error == null && content != null && !content.isBlank();
        }
    }

    /**
     * 搜索结果
     */
    public record SearchResult(String content, String documentName, Double similarity) {}
}
