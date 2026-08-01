package com.dust.wxclawbackfront.bot.agent.mcp.jobhelper;

import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpRequest;

@Configuration
@ConditionalOnProperty(prefix = "wxclaw.career", name = "enabled", havingValue = "true")
public class JobHelperMcpConfiguration {
    @Bean
    public McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> jobHelperMcpTransportCustomizer(
            JobHelperProperties properties) {
        return (connectionName, builder) -> {
            if ("job-helper".equals(connectionName)) {
                builder.connectTimeout(properties.getConnectTimeout())
                        .requestBuilder(HttpRequest.newBuilder().header("X-API-Key", properties.getApiKey()));
            }
        };
    }
}
