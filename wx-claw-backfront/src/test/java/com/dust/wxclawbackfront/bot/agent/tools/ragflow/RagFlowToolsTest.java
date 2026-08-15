package com.dust.wxclawbackfront.bot.agent.tools.ragflow;

import com.dust.wxclawbackfront.bot.ragflow.RagFlowClient;
import com.dust.wxclawbackfront.bot.agent.tools.shared.FileUploadValidator;
import com.dust.wxclawbackfront.config.security.UrlSafetyValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RagFlowToolsTest {

    @Test
    void rejectsFileNameWithoutUrlScheme() {
        RagFlowClient client = mock(RagFlowClient.class);
        RagFlowTools tools = new RagFlowTools(client, new UrlSafetyValidator(false),
                mock(FileUploadValidator.class), 10_000_000L);

        RagFlowTools.KnowledgeUploadResult result = tools.uploadToKnowledge(
                "Java后端开发-李佳霖.pdf", "李佳霖-Java后端开发简历.pdf");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("HTTP/HTTPS", "不能只传文件名");
        verifyNoInteractions(client);
    }
}
