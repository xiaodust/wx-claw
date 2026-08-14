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
import org.springframework.test.util.ReflectionTestUtils;

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
        ReflectionTestUtils.setField(service, "baseUrl", "https://ark.cn-beijing.volces.com/api/v3");
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void currentReportsNotConfiguredByDefault() {
        when(repository.findById("tenant-a")).thenReturn(Optional.empty());

        UserDtos.AiConfig config = service.current();

        assertThat(config.configured()).isFalse();
        assertThat(config.apiKeyMasked()).isNull();
    }

    @Test
    void savePersistsKeyForCurrentTenantAndEvictsFactory() {
        TenantAiConfig saved = new TenantAiConfig();
        when(repository.findById("tenant-a")).thenReturn(Optional.of(saved));
        when(repository.save(any(TenantAiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDtos.AiConfig config = service.save("  sk-abcdefgh1234  ");

        assertThat(saved.getTenantId()).isEqualTo("tenant-a");
        assertThat(saved.getApiKey()).isEqualTo("sk-abcdefgh1234");
        verify(factory).evict("tenant-a");
        assertThat(config.configured()).isTrue();
        assertThat(config.apiKeyMasked()).isEqualTo("sk-a****1234");
    }

    @Test
    void saveRejectsBlankKey() {
        assertThatThrownBy(() -> service.save("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        verify(repository, never()).save(any());
    }

    @Test
    void clearRemovesConfigAndEvictsFactory() {
        service.clear();

        verify(repository).deleteById("tenant-a");
        verify(factory).evict("tenant-a");
    }
}
