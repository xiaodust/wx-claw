package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.agent.llm.AiModelCatalog;
import com.dust.wxclawbackfront.bot.agent.llm.chat.TenantChatClientFactory;
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

    @BeforeEach
    void setUp() {
        repository = mock(TenantAiConfigRepository.class);
        factory = mock(TenantChatClientFactory.class);
        modelCatalog = new AiModelCatalog();
        service = new TenantAiConfigService(repository, factory, modelCatalog);
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
        assertThat(configs.image().provider()).contains("SiliconFlow");
        assertThat(configs.chat().provider()).contains("火山方舟");
        assertThat(configs.video().provider()).contains("Seedance");
        assertThat(configs.videoDashscope().provider()).contains("通义万相");
        assertThat(configs.tts().provider()).contains("TTS");
        assertThat(configs.search().provider()).contains("博查");
    }

    @Test
    void saveChatPersistsKeyAndEvictsFactory() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfigEntry entry = service.save("chat", "  sk-abcdefgh1234  ");

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getApiKey()).isEqualTo("sk-abcdefgh1234");
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

        assertThat(saved.getImageApiKey()).isEqualTo("sk-img-1234");
        assertThat(entry.provider()).contains("SiliconFlow");
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
}
