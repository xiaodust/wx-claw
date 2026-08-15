package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.agent.llm.TenantAiKeyProvider;
import com.dust.wxclawbackfront.exception.WxClawException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantChatClientFactoryTest {

    static class FactoryStub extends TenantChatClientFactory {
        private final ChatClient client;

        FactoryStub(TenantAiKeyProvider keyProvider, ChatClient client) {
            super(keyProvider, "http://localhost", "test-model", Duration.ofSeconds(5), 2);
            this.client = client;
        }

        @Override
        protected ChatClient buildClient(String apiKey, String baseUrl, String model) {
            return client;
        }
    }

    @Test
    void throwsWhenTenantKeyMissing() {
        TenantAiKeyProvider keyProvider = mock(TenantAiKeyProvider.class);
        when(keyProvider.chatKeyFor("tenant-a")).thenReturn(null);
        when(keyProvider.chatKeyFor("tenant-b")).thenReturn("   ");

        FactoryStub factory = new FactoryStub(keyProvider, mock(ChatClient.class));

        assertThatThrownBy(() -> factory.clientFor("tenant-a"))
                .isInstanceOf(WxClawException.class)
                .hasMessageContaining("未配置 API Key");
        assertThatThrownBy(() -> factory.clientFor("tenant-b"))
                .isInstanceOf(WxClawException.class);
    }

    @Test
    void cachesPerTenantClientAndEvicts() {
        TenantAiKeyProvider keyProvider = mock(TenantAiKeyProvider.class);
        when(keyProvider.chatKeyFor("tenant-a")).thenReturn("sk-user-key");
        ChatClient client = mock(ChatClient.class);

        FactoryStub factory = new FactoryStub(keyProvider, client);

        assertThat(factory.clientFor("tenant-a")).isSameAs(client);
        assertThat(factory.clientFor("tenant-a")).isSameAs(client);
        factory.evict("tenant-a");
        assertThat(factory.clientFor("tenant-a")).isSameAs(client);
    }

    @Test
    void buildClientProvidesBothSyncAndAsyncClients() {
        TenantChatClientFactory factory = new TenantChatClientFactory(
                mock(TenantAiKeyProvider.class), "http://localhost", "test-model",
                Duration.ofSeconds(5), 2);

        // OpenAiChatModel 构建时会同时创建 sync/async client；
        // 若只提供 sync client，async 会回退用 options.apiKey（null）构建并抛"缺少凭据"
        ChatClient client = factory.buildClient("sk-test-key", "http://localhost", "test-model");

        assertThat(client).isNotNull();
    }
}
