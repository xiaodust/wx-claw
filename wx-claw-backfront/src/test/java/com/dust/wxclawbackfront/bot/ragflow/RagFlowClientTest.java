package com.dust.wxclawbackfront.bot.ragflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RagFlowClientTest {

    private final RagFlowClient client = new RagFlowClient(
            new ObjectMapper(), "http://localhost:9380", "key", "chat", "dataset", Duration.ofSeconds(1));

    @Test
    void treatsQuotaErrorInsideHttpSuccessBodyAsFailure() {
        String response = """
                {"choices":[{"message":{"content":"**ERROR**: QUOTA_EXCEEDED - Error code: 402 - insufficient_balance"}}]}
                """;

        RagFlowClient.RagFlowResult result = client.parseResponse(response);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.content()).isNull();
        assertThat(result.error()).contains("额度不足");
    }
}
