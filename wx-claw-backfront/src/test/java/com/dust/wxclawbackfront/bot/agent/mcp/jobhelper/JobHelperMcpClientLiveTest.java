package com.dust.wxclawbackfront.bot.agent.mcp.jobhelper;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient.ResumeFile;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient.UserIdentity;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "JOB_HELPER_MCP_LIVE_TEST", matches = "true")
class JobHelperMcpClientLiveTest {
    @Test
    void callsIndependentJobHelperStreamableHttpServer() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(System.getenv().getOrDefault("JOB_HELPER_MCP_URL", "http://127.0.0.1:18081"))
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("X-API-Key",
                        System.getenv().getOrDefault("JOB_HELPER_MCP_API_KEY", "xiaodust910")))
                .build();
        McpSyncClient sdkClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .clientInfo(new McpSchema.Implementation("wx-claw-test", "1.0.0"))
                .build();
        JobHelperMcpClient client = new JobHelperMcpClient(List.of(sdkClient), new ObjectMapper());

        JobHelperDtos.JobSearchResponse result = client.search(new JobHelperDtos.JobFilters(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null), 1, 10);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        UserIdentity identity = new UserIdentity("live-test", "wx-claw/bot/user");
        ResumeFile resume = new ResumeFile("resume.pdf", "%PDF-live-test".getBytes(), "live-test-sha");
        try {
            JobHelperDtos.StoredResume saved = client.saveResume(identity, resume);
            assertThat(client.currentResume(identity).exists()).isTrue();
            assertThat(client.readResume(saved.resourceUri())).isEqualTo(resume.fileBytes());
        } finally {
            client.deleteResume(identity);
        }
        sdkClient.close();
    }
}
