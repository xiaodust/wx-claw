package com.dust.wxclawbackfront.bot.agent.llm;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantAiKeyProviderTest {

    private TenantAiConfigRepository repository;
    private TenantAiKeyProvider provider;

    @BeforeEach
    void setUp() {
        repository = mock(TenantAiConfigRepository.class);
        provider = new TenantAiKeyProvider(repository);
        ReflectionTestUtils.setField(provider, "defaultChatKey", "sk-default-chat");
        ReflectionTestUtils.setField(provider, "defaultImageKey", "sk-default-image");
        ReflectionTestUtils.setField(provider, "defaultVideoDashscopeKey", "sk-default-dash");
        ReflectionTestUtils.setField(provider, "defaultTtsKey", "sk-default-tts");
        ReflectionTestUtils.setField(provider, "defaultSearchKey", "sk-default-search");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void chatKeyUsesTenantOverride() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setApiKey("  sk-user-chat  ");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.chatKey()).isEqualTo("sk-user-chat");
        assertThat(provider.chatKeyFor("tenant-a")).isEqualTo("sk-user-chat");
    }

    @Test
    void imageKeyUsesTenantOverride() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setImageApiKey("sk-user-image");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.imageKey()).isEqualTo("sk-user-image");
    }

    @Test
    void fallsBackToDefaultsWhenNoTenantConfig() {
        when(repository.findById("tenant-a")).thenReturn(Optional.empty());
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.chatKey()).isEqualTo("sk-default-chat");
        assertThat(provider.imageKey()).isEqualTo("sk-default-image");
        assertThat(provider.videoKey()).isEqualTo("sk-default-chat");
        assertThat(provider.videoDashscopeKey()).isEqualTo("sk-default-dash");
        assertThat(provider.ttsKey()).isEqualTo("sk-default-tts");
        assertThat(provider.searchKey()).isEqualTo("sk-default-search");
    }

    @Test
    void fallsBackToDefaultsWithoutTenantContext() {
        assertThat(provider.imageKey()).isEqualTo("sk-default-image");
        assertThat(provider.chatKey()).isEqualTo("sk-default-chat");
    }
}
