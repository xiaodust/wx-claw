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
        provider = new TenantAiKeyProvider(repository, new AiModelCatalog());
        ReflectionTestUtils.setField(provider, "defaultChatKey", "sk-default-chat");
        ReflectionTestUtils.setField(provider, "defaultChatBaseUrl", "https://ark.cn-beijing.volces.com/api/v3");
        ReflectionTestUtils.setField(provider, "defaultChatModel", "doubao-seed-2-1-turbo-260628");
        ReflectionTestUtils.setField(provider, "defaultImageKey", "sk-default-image");
        ReflectionTestUtils.setField(provider, "defaultImageProvider", "siliconflow");
        ReflectionTestUtils.setField(provider, "defaultImageModel", "Kwai-Kolors/Kolors");
        ReflectionTestUtils.setField(provider, "defaultVideoDashscopeKey", "sk-default-dash");
        ReflectionTestUtils.setField(provider, "defaultVideoModel", "doubao-seedance-2-0-mini-260615");
        ReflectionTestUtils.setField(provider, "defaultVideoProvider", "ark");
        ReflectionTestUtils.setField(provider, "defaultVideoKey", "sk-default-video");
        ReflectionTestUtils.setField(provider, "defaultOpenaiVideoKey", "sk-default-openai-video");
        ReflectionTestUtils.setField(provider, "defaultOpenaiVideoModel", "sora-2");
        ReflectionTestUtils.setField(provider, "defaultOpenaiVideoBaseUrl", "https://api.openai.com/v1");
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
        assertThat(provider.chatProvider()).isEqualTo("ark");
        assertThat(provider.chatBaseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
        assertThat(provider.chatModel()).isEqualTo("doubao-seed-2-1-turbo-260628");
        assertThat(provider.imageProvider()).isEqualTo("siliconflow");
        assertThat(provider.imageModel()).isEqualTo("Kwai-Kolors/Kolors");
        assertThat(provider.videoProvider()).isEqualTo("ark");
        assertThat(provider.videoModel()).isEqualTo("doubao-seedance-2-0-mini-260615");
        assertThat(provider.imageBaseUrlFor("openai")).isEqualTo("https://api.openai.com/v1");
        assertThat(provider.videoBaseUrlFor("openai")).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void fallsBackToDefaultsWithoutTenantContext() {
        assertThat(provider.imageKey()).isEqualTo("sk-default-image");
        assertThat(provider.chatKey()).isEqualTo("sk-default-chat");
        assertThat(provider.chatBaseUrl()).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
    }

    @Test
    void chatModelAndBaseUrlFollowTenantProvider() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setChatProvider("openai");
        config.setChatBaseUrl("https://api.openai.com/v1");
        config.setChatModel("gpt-4o-mini");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.chatProvider()).isEqualTo("openai");
        assertThat(provider.chatBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(provider.chatModel()).isEqualTo("gpt-4o-mini");
        assertThat(provider.chatBaseUrlFor("tenant-a")).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void videoKeyPrefersOwnConfiguredKey() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setChatProvider("openai");
        config.setVideoApiKey("sk-own-video");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.videoKey()).isEqualTo("sk-own-video");
    }

    @Test
    void videoKeyReusesChatKeyWhenChatProviderIsArk() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setChatProvider("ark");
        config.setApiKey("sk-ark-chat");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.videoKey()).isEqualTo("sk-ark-chat");
    }

    @Test
    void videoKeyFallsBackToDefaultWhenChatIsNotArk() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setChatProvider("openai");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.videoKey()).isEqualTo("sk-default-video");
    }

    @Test
    void imageProviderFollowsTenantOverride() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setImageProvider("ark");
        config.setImageModel("doubao-seedream-4-0-250828");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.imageProvider()).isEqualTo("ark");
        assertThat(provider.imageModel()).isEqualTo("doubao-seedream-4-0-250828");
        assertThat(provider.imageBaseUrlFor("ark")).isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
    }

    @Test
    void videoProviderFollowsTenantOverride() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setVideoProvider("openai");
        config.setVideoModel("sora-2-pro");
        config.setVideoApiKey("sk-openai-video");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.videoProvider()).isEqualTo("openai");
        assertThat(provider.videoModel()).isEqualTo("sora-2-pro");
        assertThat(provider.videoKey()).isEqualTo("sk-openai-video");
        assertThat(provider.videoBaseUrlFor("openai")).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void openaiVideoFallsBackToOpenaiDefaultKeyAndModel() {
        TenantAiConfig config = new TenantAiConfig();
        config.setTenantId("tenant-a");
        config.setVideoProvider("openai");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(config));
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));

        assertThat(provider.videoKey()).isEqualTo("sk-default-openai-video");
        assertThat(provider.videoModel()).isEqualTo("sora-2");
    }
}
