package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.agent.llm.AiModelCatalog;
import com.dust.wxclawbackfront.bot.agent.llm.chat.TenantChatClientFactory;
import com.dust.wxclawbackfront.config.security.TenantAiKeyCipher;
import com.dust.wxclawbackfront.config.security.UrlSafetyValidator;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAiConfigServiceTest {

    private TenantAiConfigRepository repository;
    private TenantChatClientFactory factory;
    private AiModelCatalog modelCatalog;
    private TenantAiConfigService service;
    private TenantAiKeyCipher keyCipher;

    @BeforeEach
    void setUp() {
        repository = mock(TenantAiConfigRepository.class);
        factory = mock(TenantChatClientFactory.class);
        modelCatalog = new AiModelCatalog();
        keyCipher = new TenantAiKeyCipher("test-key");
        service = new TenantAiConfigService(repository, factory, modelCatalog,
                new UrlSafetyValidator(false), keyCipher);
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void currentReportsAllCapabilitiesUnconfigured() {
        when(repository.findById("tenant-a")).thenReturn(Optional.empty());

        UserDtos.AiConfigs configs = service.current();

        assertThat(configs.chat().configured()).isFalse();
        assertThat(configs.chat().apiKeyMasked()).isNull();
        assertThat(configs.image().provider()).isEqualTo("siliconflow");
        assertThat(configs.chat().provider()).isEqualTo("ark");
        assertThat(configs.video().provider()).isEqualTo("ark");
        assertThat(configs.videoDashscope().provider()).isEqualTo("dashscope");
        assertThat(configs.tts().provider()).isEqualTo("tts");
        assertThat(configs.search().provider()).isEqualTo("search");
    }

    @Test
    void saveChatPersistsKeyAndEvictsFactory() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.save("chat", "  sk-abcdefgh1234  ");

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(keyCipher.decrypt(saved.getApiKey())).isEqualTo("sk-abcdefgh1234");
        verify(factory).evict("tenant-a");
        assertThat(entry.configured()).isTrue();
        assertThat(entry.apiKeyMasked()).isEqualTo("sk-a****1234");
        assertThat(entry.model()).isNull();
    }

    @Test
    void saveImageKeyDoesNotEvictChatFactory() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.save("image", "sk-img-1234");

        assertThat(keyCipher.decrypt(saved.getImageApiKey())).isEqualTo("sk-img-1234");
        assertThat(entry.provider()).isEqualTo("siliconflow");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveVideoKeyPersists() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.save("video", "sk-video-1234");

        assertThat(keyCipher.decrypt(saved.getVideoApiKey())).isEqualTo("sk-video-1234");
        assertThat(entry.provider()).isEqualTo("ark");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveVideoKeyRoutesToDashscopeFieldWhenProviderIsDashscope() {
        TenantAiConfig saved = new TenantAiConfig();
        saved.setVideoProvider("dashscope");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.save("video", "sk-dash-1234");

        assertThat(saved.getVideoApiKey()).isNull();
        assertThat(keyCipher.decrypt(saved.getVideoDashscopeApiKey())).isEqualTo("sk-dash-1234");
        assertThat(entry.configured()).isTrue();
        assertThat(entry.provider()).isEqualTo("dashscope");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveRejectsBlankKey() {
        assertThatThrownBy(() -> service.save("chat", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        verify(repository, never()).save(any());
    }

    @Test
    void saveRejectsUnknownCapability() {
        assertThatThrownBy(() -> service.save("unknown", "sk-x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知能力");
    }

    @Test
    void clearCapabilityNullsOnlyThatKey() {
        TenantAiConfig saved = new TenantAiConfig();
        saved.setApiKey("sk-chat");
        saved.setTtsApiKey("sk-tts");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.clear("tts");

        assertThat(saved.getTtsApiKey()).isNull();
        assertThat(saved.getApiKey()).isEqualTo("sk-chat");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveChatModelSetsProviderAndDerivesBaseUrl() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.saveModel("chat",
                new UserDtos.UpdateModelRequest("gpt-4o-mini", "openai", null));

        assertThat(saved.getChatProvider()).isEqualTo("openai");
        assertThat(saved.getChatBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(saved.getChatModel()).isEqualTo("gpt-4o-mini");
        verify(factory).evict("tenant-a");
        assertThat(entry.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void saveChatModelRejectsUnknownProvider() {
        assertThatThrownBy(() -> service.saveModel("chat",
                new UserDtos.UpdateModelRequest("x", "unknown", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知服务商");
    }

    @Test
    void saveModelOnNonConfigurableCapabilityRejected() {
        assertThatThrownBy(() -> service.saveModel("tts",
                new UserDtos.UpdateModelRequest("seed-audio-1.0", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持模型自定义");
    }

    @Test
    void clearChatModelResetsProviderAndBaseUrl() {
        TenantAiConfig saved = new TenantAiConfig();
        saved.setChatProvider("openai");
        saved.setChatBaseUrl("https://api.openai.com/v1");
        saved.setChatModel("gpt-4o-mini");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.clearModel("chat");

        assertThat(saved.getChatModel()).isNull();
        assertThat(saved.getChatProvider()).isNull();
        assertThat(saved.getChatBaseUrl()).isNull();
        verify(factory).evict("tenant-a");
    }

    @Test
    void saveImageModelSetsProviderAndModel() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.saveModel("image",
                new UserDtos.UpdateModelRequest("gpt-image-1", "openai", null));

        assertThat(saved.getImageProvider()).isEqualTo("openai");
        assertThat(saved.getImageModel()).isEqualTo("gpt-image-1");
        assertThat(entry.provider()).isEqualTo("openai");
        assertThat(entry.model()).isEqualTo("gpt-image-1");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveVideoModelSetsProviderAndModel() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.saveModel("video",
                new UserDtos.UpdateModelRequest("sora-2", "openai", null));

        assertThat(saved.getVideoProvider()).isEqualTo("openai");
        assertThat(saved.getVideoModel()).isEqualTo("sora-2");
        assertThat(entry.provider()).isEqualTo("openai");
        assertThat(entry.model()).isEqualTo("sora-2");
        verify(factory, never()).evict(any());
    }

    @Test
    void saveImageModelRejectsUnknownProvider() {
        assertThatThrownBy(() -> service.saveModel("image",
                new UserDtos.UpdateModelRequest("x", "unknown", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知服务商");
    }

    @Test
    void saveVideoModelRejectsUnknownProvider() {
        assertThatThrownBy(() -> service.saveModel("video",
                new UserDtos.UpdateModelRequest("x", "unknown", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知服务商");
    }

    @Test
    void clearImageModelResetsProvider() {
        TenantAiConfig saved = new TenantAiConfig();
        saved.setImageProvider("openai");
        saved.setImageModel("gpt-image-1");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.clearModel("image");

        assertThat(saved.getImageModel()).isNull();
        assertThat(saved.getImageProvider()).isNull();
    }

    @Test
    void clearVideoCapabilityClearsBothKeyFields() {
        TenantAiConfig saved = new TenantAiConfig();
        saved.setVideoProvider("dashscope");
        saved.setVideoApiKey("sk-ark");
        saved.setVideoDashscopeApiKey("sk-dash");
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.clear("video");

        assertThat(saved.getVideoApiKey()).isNull();
        assertThat(saved.getVideoDashscopeApiKey()).isNull();
    }
}
