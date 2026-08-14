package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantChatClientFactoryTest {

    @Test
    void fallsBackToDefaultClientWithoutTenantConfig() {
        ChatClient defaultClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(defaultClient);
        TenantAiConfigRepository repository = mock(TenantAiConfigRepository.class);

        FactoryStub factory = new FactoryStub(builder, repository, defaultClient);

        assertThat(factory.clientFor("tenant-a")).isSameAs(defaultClient);
        assertThat(factory.clientFor("tenant-a")).isSameAs(defaultClient);
        assertThat(factory.clientFor(null)).isSameAs(defaultClient);
        assertThat(factory.clientFor("")).isSameAs(defaultClient);
    }

    @Test
    void cachesPerTenantClientAndEvictsOnChange() {
        ChatClient defaultClient = mock(ChatClient.class);
        ChatClient tenantClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(defaultClient);
        TenantAiConfigRepository repository = mock(TenantAiConfigRepository.class);
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setApiKey("  sk-test-key  ");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));

        FactoryStub factory = new FactoryStub(builder, repository, tenantClient);

        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
        factory.evict("tenant-a");
        assertThat(factory.clientFor("tenant-a")).isSameAs(tenantClient);
    }

    private static final class FactoryStub extends TenantChatClientFactory {
        private final ChatClient tenantClient;

        private FactoryStub(ChatClient.Builder builder, TenantAiConfigRepository repository,
                            ChatClient tenantClient) {
            super(builder, repository, "http://localhost", "test-model",
                    Duration.ofSeconds(5), 2);
            this.tenantClient = tenantClient;
        }

        @Override
        protected ChatClient buildClient(String apiKey) {
            return tenantClient;
        }
    }
}
