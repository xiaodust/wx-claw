package com.dust.wxclawbackfront.user.service;

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
    private TenantAiConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantAiConfigRepository.class);
        factory = mock(TenantChatClientFactory.class);
        service = new TenantAiConfigService(repository, factory);
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
        assertThat(configs.video().provider()).contains("Seedance");
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
}
