package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.agent.llm.TenantAiKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantChatClientFactoryTest {

    @Test
    void fallsBackToDefaultClientWithoutTenantKey() {
        ChatClient defaultClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(defaultClient);
        TenantAiKeyProvider keyProvider = mock(TenantAiKeyProvider.class);
        when(keyProvider.chatKeyFor("tenant-a")).thenReturn(null);
        when(keyProvider.chatKeyFor("tenant-b")).thenReturn("  ");

        FactoryStub factory = new FactoryStub(builder, keyProvider, defaultClient);

        assertThat(factory.clientFor("tenant-a")).isSameAs(defaultClient);
        assertThat(factory.clientFor("tenant-b")).isSameAs(defaultClient);
        assertThat(factory.clientFor(null)).isSameAs(defaultClient);
    }

    @Test
    void cachesPerTenantClientAndEvicts() {
        ChatClient defaultClient = mock(ChatClient.class);
        ChatClient tenantClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(defaultClient);
        TenantAiKeyProvider keyProvider = mock(TenantAiKeyProvider.class);
        when(keyProvider.chatKeyFor("tenant-a")).thenReturn("sk-user-key");

        FactoryStub factory = new FactoryStub(builder, keyProvider, tenantClient);

        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
        factory.evict("tenant-a");
        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
    }

    private static final class FactoryStub extends TenantChatClientFactory {
        private final ChatClient tenantClient;

        private FactoryStub(ChatClient.Builder builder, TenantAiKeyProvider keyProvider,
                            ChatClient tenantClient) {
            super(builder, keyProvider, "http://localhost", "test-model",
                    Duration.ofSeconds(5), 2);
            this.tenantClient = tenantClient;
        }

        @Override
        protected ChatClient buildClient(String apiKey) {
            return tenantClient;
        }
    }
}
