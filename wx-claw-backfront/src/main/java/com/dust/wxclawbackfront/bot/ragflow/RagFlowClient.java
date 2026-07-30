package com.dust.wxclawbackfront.bot.ragflow;

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
    RagFlowResult parseResponse(String responseText) {
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
                    return contentResult(content, reference);
                }
            }

            // 尝试直接从响应中提取 content（某些 RAGFlow 版本）
            Object directContent = responseMap.get("content");
            if (directContent instanceof String && !((String) directContent).isBlank()) {
                return contentResult((String) directContent, responseMap.get("reference"));
            }

            // 尝试从 data 中提取
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data != null) {
                Object dataContent = data.get("content");
                if (dataContent instanceof String && !((String) dataContent).isBlank()) {
                    return contentResult((String) dataContent, data.get("reference"));
                }
            }

            log.warn("RAGFlow 响应格式无法解析，keys: {}", responseMap.keySet());
            return new RagFlowResult(null, null, "无回答内容");

        } catch (Exception ex) {
            log.error("解析 RAGFlow 响应失败: {}", ex.getMessage());
            return new RagFlowResult(null, null, "解析响应失败");
        }
    }

    private RagFlowResult contentResult(String content, Object reference) {
        String normalized = content.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("**ERROR**") || normalized.startsWith("ERROR:")) {
            String error;
            if (normalized.contains("QUOTA_EXCEEDED") || normalized.contains("INSUFFICIENT_BALANCE")) {
                error = "RAGFlow 下游模型额度不足，请检查模型供应商余额";
            } else {
                error = "RAGFlow 下游模型调用失败";
            }
            log.error("RAGFlow 在成功响应中返回业务错误: {}", error);
            return new RagFlowResult(null, reference, error);
        }
        return new RagFlowResult(content, reference, null);
    }

    /**
     * 上传文档到知识库
     *
     * @param file     文件的字节数组
     * @param fileName 文件名
     * @return 上传结果
     */
    public UploadResult uploadDocument(byte[] file, String fileName) {
        if (datasetId == null || datasetId.isBlank()) {
            return new UploadResult(false, null, "未配置 RAGFlow dataset-id");
        }

        try {
            String url = baseUrl + "/api/v1/datasets/" + datasetId + "/documents";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";

            // 构建 multipart 请求体
            var outputStream = new java.io.ByteArrayOutputStream();

            // 文件部分
            outputStream.write(("--" + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + lineEnd).getBytes());
            outputStream.write(("Content-Type: application/octet-stream" + lineEnd + lineEnd).getBytes());
            outputStream.write(file);
            outputStream.write(lineEnd.getBytes());

            // 结束标记
            outputStream.write(("--" + boundary + "--" + lineEnd).getBytes());

            byte[] requestBody = outputStream.toByteArray();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60)) // 上传文件可能需要更长时间
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            log.info("RAGFlow 上传响应: HTTP {}, body={}", response.statusCode(),
                    responseText != null && responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 上传失败: HTTP {}, body={}", response.statusCode(), responseText);
                return new UploadResult(false, null, "RAGFlow 上传失败: HTTP " + response.statusCode());
            }

            return parseUploadResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 上传异常: {}", ex.getMessage(), ex);
            return new UploadResult(false, null, ex.getMessage());
        }
    }

    /**
     * 解析上传响应
     */
    @SuppressWarnings("unchecked")
    private UploadResult parseUploadResponse(String responseText) {
        try {
            // 先尝试解析为Map
            Object responseObject = objectMapper.readValue(responseText, Object.class);
            
            // 如果是数组，取第一个元素
            if (responseObject instanceof List) {
                List<Object> responseList = (List<Object>) responseObject;
                if (responseList.isEmpty()) {
                    return new UploadResult(false, null, "响应为空数组");
                }
                responseObject = responseList.get(0);
            }
            
            // 转换为Map
            if (!(responseObject instanceof Map)) {
                log.error("RAGFlow 上传响应格式异常: {}", responseText);
                return new UploadResult(false, null, "响应格式异常");
            }
            
            Map<String, Object> responseMap = (Map<String, Object>) responseObject;

            // 检查是否有错误响应
            if (responseMap.containsKey("code") && responseMap.containsKey("message")) {
                Object code = responseMap.get("code");
                String message = (String) responseMap.get("message");
                // 如果 code 不是 0 或 200，表示错误
                if (code instanceof Number && ((Number) code).intValue() != 0 && ((Number) code).intValue() != 200) {
                    log.error("RAGFlow 上传返回错误: code={}, message={}", code, message);
                    return new UploadResult(false, null, "RAGFlow 错误: " + message);
                }
            }

            // 提取文档信息（data 可能是数组或对象）
            Object dataObj = responseMap.get("data");
            if (dataObj instanceof List) {
                // data 是数组，取第一个元素
                List<?> dataList = (List<?>) dataObj;
                if (!dataList.isEmpty() && dataList.get(0) instanceof Map) {
                    Map<String, Object> doc = (Map<String, Object>) dataList.get(0);
                    String documentId = (String) doc.get("id");
                    String documentName = (String) doc.get("name");
                    return new UploadResult(true, documentId, "文件上传成功: " + documentName);
                }
            } else if (dataObj instanceof Map) {
                // data 是对象
                Map<String, Object> data = (Map<String, Object>) dataObj;
                String documentId = (String) data.get("id");
                String documentName = (String) data.get("name");
                return new UploadResult(true, documentId, "文件上传成功: " + documentName);
            }

            // 如果没有data字段，尝试直接从响应中提取
            String documentId = (String) responseMap.get("id");
            String documentName = (String) responseMap.get("name");
            if (documentId != null || documentName != null) {
                return new UploadResult(true, documentId, "文件上传成功: " + documentName);
            }

            return new UploadResult(true, null, "文件上传成功");

        } catch (Exception ex) {
            log.error("解析 RAGFlow 上传响应失败: {}", ex.getMessage());
            return new UploadResult(false, null, "解析响应失败");
        }
    }

    /**
     * 列举知识库中的所有文档
     *
     * @return 文档列表
     */
    public List<DocumentInfo> listDocuments() {
        if (datasetId == null || datasetId.isBlank()) {
            return List.of();
        }

        try {
            String url = baseUrl + "/api/v1/datasets/" + datasetId + "/documents";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            log.info("RAGFlow 列举文档响应: HTTP {}, body={}", response.statusCode(),
                    responseText != null && responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 列举文档失败: HTTP {}", response.statusCode());
                return List.of();
            }

            return parseDocumentListResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 列举文档异常: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * 删除知识库中的文档
     *
     * @param documentIds 文档ID列表
     * @return 删除结果
     */
    public DeleteResult deleteDocuments(List<String> documentIds) {
        if (datasetId == null || datasetId.isBlank()) {
            return new DeleteResult(false, "未配置 RAGFlow dataset-id");
        }

        if (documentIds == null || documentIds.isEmpty()) {
            return new DeleteResult(false, "文档ID列表不能为空");
        }

        try {
            String url = baseUrl + "/api/v1/datasets/" + datasetId + "/documents";

            Map<String, Object> requestBody = Map.of("ids", documentIds);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            log.info("RAGFlow 删除文档响应: HTTP {}, body={}", response.statusCode(),
                    responseText != null && responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 删除文档失败: HTTP {}, body={}", response.statusCode(), responseText);
                return new DeleteResult(false, "RAGFlow 删除失败: HTTP " + response.statusCode());
            }

            return parseDeleteResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 删除文档异常: {}", ex.getMessage(), ex);
            return new DeleteResult(false, ex.getMessage());
        }
    }

    /**
     * 更新知识库中的文档
     *
     * @param documentId 文档ID
     * @param name       新的文档名称（可选）
     * @return 更新结果
     */
    public UpdateResult updateDocument(String documentId, String name) {
        if (datasetId == null || datasetId.isBlank()) {
            return new UpdateResult(false, "未配置 RAGFlow dataset-id");
        }

        if (documentId == null || documentId.isBlank()) {
            return new UpdateResult(false, "文档ID不能为空");
        }

        try {
            String url = baseUrl + "/api/v1/datasets/" + datasetId + "/documents/" + documentId;

            Map<String, Object> requestBody = new java.util.HashMap<>();
            if (name != null && !name.isBlank()) {
                requestBody.put("name", name);
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();

            log.info("RAGFlow 更新文档响应: HTTP {}, body={}", response.statusCode(),
                    responseText != null && responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText);

            if (response.statusCode() / 100 != 2) {
                log.error("RAGFlow 更新文档失败: HTTP {}, body={}", response.statusCode(), responseText);
                return new UpdateResult(false, "RAGFlow 更新失败: HTTP " + response.statusCode());
            }

            return parseUpdateResponse(responseText);

        } catch (Exception ex) {
            log.error("RAGFlow 更新文档异常: {}", ex.getMessage(), ex);
            return new UpdateResult(false, ex.getMessage());
        }
    }

    /**
     * 解析文档列表响应
     */
    @SuppressWarnings("unchecked")
    private List<DocumentInfo> parseDocumentListResponse(String responseText) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return List.of();
            }

            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data == null) {
                return List.of();
            }

            List<Map<String, Object>> docs = (List<Map<String, Object>>) data.get("docs");
            if (docs == null) {
                return List.of();
            }

            List<DocumentInfo> results = new ArrayList<>();
            for (Map<String, Object> doc : docs) {
                String id = (String) doc.get("id");
                String name = (String) doc.get("name");
                String status = (String) doc.get("status");
                Long size = doc.get("size") != null ? ((Number) doc.get("size")).longValue() : null;
                String chunkMethod = (String) doc.get("chunk_method");

                if (id != null) {
                    results.add(new DocumentInfo(id, name, status, size, chunkMethod));
                }
            }

            return results;

        } catch (Exception ex) {
            log.error("解析 RAGFlow 文档列表响应失败: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 解析删除响应
     */
    @SuppressWarnings("unchecked")
    private DeleteResult parseDeleteResponse(String responseText) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return new DeleteResult(false, "响应为空");
            }

            // 检查是否有错误响应
            if (responseMap.containsKey("code") && responseMap.containsKey("message")) {
                Object code = responseMap.get("code");
                String message = (String) responseMap.get("message");
                if (code instanceof Number && ((Number) code).intValue() != 0 && ((Number) code).intValue() != 200) {
                    return new DeleteResult(false, "RAGFlow 错误: " + message);
                }
            }

            return new DeleteResult(true, "文档删除成功");

        } catch (Exception ex) {
            log.error("解析 RAGFlow 删除响应失败: {}", ex.getMessage());
            return new DeleteResult(false, "解析响应失败");
        }
    }

    /**
     * 解析更新响应
     */
    @SuppressWarnings("unchecked")
    private UpdateResult parseUpdateResponse(String responseText) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responseText, Map.class);
            if (responseMap == null) {
                return new UpdateResult(false, "响应为空");
            }

            // 检查是否有错误响应
            if (responseMap.containsKey("code") && responseMap.containsKey("message")) {
                Object code = responseMap.get("code");
                String message = (String) responseMap.get("message");
                if (code instanceof Number && ((Number) code).intValue() != 0 && ((Number) code).intValue() != 200) {
                    return new UpdateResult(false, "RAGFlow 错误: " + message);
                }
            }

            return new UpdateResult(true, "文档更新成功");

        } catch (Exception ex) {
            log.error("解析 RAGFlow 更新响应失败: {}", ex.getMessage());
            return new UpdateResult(false, "解析响应失败");
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

    /**
     * 上传结果
     */
    public record UploadResult(boolean success, String documentId, String message) {}

    /**
     * 文档信息
     */
    public record DocumentInfo(String id, String name, String status, Long size, String chunkMethod) {}

    /**
     * 删除结果
     */
    public record DeleteResult(boolean success, String message) {}

    /**
     * 更新结果
     */
    public record UpdateResult(boolean success, String message) {}
}
