package com.dust.wxclawbackfront.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcArkEmbeddingClientTest {

    private static final int DIMENSIONS = 1024;

    private HttpServer server;
    private VolcArkEmbeddingClient client;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<Boolean> failFirstRequest = new AtomicReference<>(false);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/embeddings/multimodal", this::handle);
        server.start();
        int port = server.getAddress().getPort();
        client = new VolcArkEmbeddingClient(
                new ObjectMapper(),
                "http://127.0.0.1:" + port + "/api/v3",
                "test-key",
                "doubao-embedding-vision-251215",
                DIMENSIONS,
                Duration.ofSeconds(5),
                2);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        requestCount.incrementAndGet();
        lastPath.set(exchange.getRequestURI().getPath());
        lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (Boolean.TRUE.equals(failFirstRequest.getAndSet(false))) {
            exchange.close(); // 模拟网络层中断，触发客户端重试
            return;
        }
        int status = responseStatus.get();
        byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void embedsSingleTextThroughMultimodalEndpoint() {
        responseBody.set(flatEmbeddingResponseJson(DIMENSIONS));

        float[] vector = client.embed("测试向量记忆");

        assertThat(vector).hasSize(DIMENSIONS);
        assertThat(vector[0]).isEqualTo(0.0f);
        assertThat(vector[1]).isEqualTo(0.1f);
        assertThat(lastPath.get()).isEqualTo("/api/v3/embeddings/multimodal");
        assertThat(lastAuth.get()).isEqualTo("Bearer test-key");
        assertThat(lastBody.get())
                .contains("\"model\":\"doubao-embedding-vision-251215\"")
                .contains("\"dimensions\":1024")
                .contains("\"type\":\"text\"")
                .contains("测试向量记忆");
    }

    @Test
    void parsesDocumentedArrayOfItemsEnvelope() {
        responseBody.set(arrayOfItemsEmbeddingResponseJson(DIMENSIONS));

        float[] vector = client.embed("兼容旧外壳");

        assertThat(vector).hasSize(DIMENSIONS);
        assertThat(vector[1]).isEqualTo(0.1f);
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> client.embed("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void throwsWhenHttpStatusIsError() {
        responseStatus.set(400);
        responseBody.set("{\"error\":{\"message\":\"model does not support this api\"}}");

        assertThatThrownBy(() -> client.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("does not support");
    }

    @Test
    void retriesTransientNetworkFailure() {
        responseBody.set(flatEmbeddingResponseJson(DIMENSIONS));
        failFirstRequest.set(true);

        float[] vector = client.embed("重试场景");

        assertThat(vector).hasSize(DIMENSIONS);
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void rejectsVectorDimensionMismatch() {
        responseBody.set(flatEmbeddingResponseJson(3));

        assertThatThrownBy(() -> client.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("维度与配置不符")
                .hasMessageContaining("期望 " + DIMENSIONS);
    }

    @Test
    void exposesConfiguredDimensions() {
        assertThat(client.dimensions()).isEqualTo(DIMENSIONS);
    }

    /**
     * 实测 doubao-embedding-vision-251215 返回：{"data":{"embedding":[...]}}（扁平数组）。
     */
    private static String flatEmbeddingResponseJson(int dim) {
        return "{\"created\":1785921772,\"data\":{\"embedding\":[" + vectorValues(dim) + "]},"
                + "\"usage\":{\"prompt_tokens\":21,\"total_tokens\":21}}";
    }

    /**
     * 文档示例外壳：{"data":[{"object":"embedding","embedding":[[...]]}]}（嵌套二维数组）。
     */
    private static String arrayOfItemsEmbeddingResponseJson(int dim) {
        return "{\"object\":\"list\",\"model\":\"doubao-embedding-vision-251215\",\"data\":["
                + "{\"object\":\"embedding\",\"embedding\":[[" + vectorValues(dim) + "]]}],"
                + "\"usage\":{\"total_tokens\":1}}";
    }

    private static String vectorValues(int dim) {
        StringBuilder sb = new StringBuilder();
        for (int d = 0; d < dim; d++) {
            if (d > 0) {
                sb.append(',');
            }
            sb.append(d % 10 / 10.0);
        }
        return sb.toString();
    }
}
