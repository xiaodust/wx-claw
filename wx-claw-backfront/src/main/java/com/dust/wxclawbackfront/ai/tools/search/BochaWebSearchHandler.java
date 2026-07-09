package com.dust.wxclawbackfront.ai.tools.search;

import com.dust.wxclawbackfront.ai.tools.shared.TextSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BochaWebSearchHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String url;
    private final Duration timeout;
    private final int defaultCount;
    private final boolean defaultSummary;
    private final String defaultFreshness;

    public BochaWebSearchHandler(ObjectMapper objectMapper,
                                 @Value("${wxclaw.ai.web-search.bocha.api-key:}") String apiKey,
                                 @Value("${wxclaw.ai.web-search.bocha.url:https://api.bochaai.com/v1/web-search}") String url,
                                 @Value("${wxclaw.ai.web-search.bocha.timeout:PT15S}") Duration timeout,
                                 @Value("${wxclaw.ai.web-search.bocha.default-count:5}") int defaultCount,
                                 @Value("${wxclaw.ai.web-search.bocha.default-summary:true}") boolean defaultSummary,
                                 @Value("${wxclaw.ai.web-search.bocha.default-freshness:noLimit}") String defaultFreshness) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = apiKey;
        this.url = url;
        this.timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        this.defaultCount = defaultCount;
        this.defaultSummary = defaultSummary;
        this.defaultFreshness = defaultFreshness;
    }

    public BochaWebSearchResult search(String query, String freshness, Integer count) {
        String actualQuery = query == null ? null : query.trim();
        if (actualQuery == null || actualQuery.isBlank()) {
            return new BochaWebSearchResult(null, null, query, freshness, count, "搜索词不能为空", List.of());
        }
        String key = apiKey == null ? null : apiKey.trim();
        if (key == null || key.isBlank()) {
            return new BochaWebSearchResult(null, null, actualQuery, freshness, count, "未配置博查 API Key（wxclaw.ai.web-search.bocha.api-key）", List.of());
        }
        String endpoint = url == null ? null : url.trim();
        if (endpoint == null || endpoint.isBlank()) {
            return new BochaWebSearchResult(null, null, actualQuery, freshness, count, "未配置博查搜索 URL", List.of());
        }

        int actualCount = count == null ? defaultCount : Math.max(1, Math.min(count, 10));
        String actualFreshness = freshness == null || freshness.isBlank() ? defaultFreshness : freshness.trim();

        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "query", actualQuery,
                    "freshness", actualFreshness,
                    "summary", defaultSummary,
                    "count", actualCount
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(body));
            if (response.statusCode() / 100 != 2) {
                return new BochaWebSearchResult(requestJson, responseJson, actualQuery, actualFreshness, actualCount, "联网搜索失败，HTTP " + response.statusCode(), List.of());
            }
            return new BochaWebSearchResult(requestJson, responseJson, actualQuery, actualFreshness, actualCount, null, parseItems(body));
        } catch (Exception ex) {
            return new BochaWebSearchResult(null, null, actualQuery, actualFreshness, actualCount, ex.getMessage(), List.of());
        }
    }

    public String formatReply(BochaWebSearchResult result) {
        if (result == null) {
            return "联网搜索失败。";
        }
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            return "联网搜索失败：" + result.getErrorMsg();
        }
        if (result.getItems() == null || result.getItems().isEmpty()) {
            return "未找到相关网页结果。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("联网搜索结果：").append(result.getQuery());
        int limit = Math.min(result.getItems().size(), 5);
        for (int i = 0; i < limit; i++) {
            BochaWebSearchResult.Item item = result.getItems().get(i);
            sb.append("\n").append(i + 1).append(". ");
            sb.append(blankDefault(item.getName(), "未命名结果"));
            if (item.getSiteName() != null && !item.getSiteName().isBlank()) {
                sb.append(" - ").append(item.getSiteName().trim());
            }
            if (item.getSummary() != null && !item.getSummary().isBlank()) {
                sb.append("\n   ").append(item.getSummary().trim());
            } else if (item.getSnippet() != null && !item.getSnippet().isBlank()) {
                sb.append("\n   ").append(item.getSnippet().trim());
            }
            if (item.getUrl() != null && !item.getUrl().isBlank()) {
                sb.append("\n   ").append(item.getUrl().trim());
            }
        }
        return sb.toString();
    }

    private List<BochaWebSearchResult.Item> parseItems(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode webPages = root.path("webPages").path("value");
            if (!webPages.isArray()) {
                webPages = root.path("data").path("webPages").path("value");
            }
            List<BochaWebSearchResult.Item> items = new ArrayList<>();
            if (!webPages.isArray()) {
                return items;
            }
            for (JsonNode node : webPages) {
                items.add(new BochaWebSearchResult.Item(
                        text(node, "name"),
                        text(node, "url"),
                        text(node, "snippet"),
                        text(node, "summary"),
                        text(node, "siteName"),
                        text(node, "datePublished")
                ));
            }
            return items;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String toPrettyJsonOrRaw(String text) {
        try {
            Object json = objectMapper.readValue(text, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception ex) {
            return text;
        }
    }
}
