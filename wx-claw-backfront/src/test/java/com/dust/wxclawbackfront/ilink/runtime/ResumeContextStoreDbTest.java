package com.dust.wxclawbackfront.ilink.runtime;

import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeContextStoreDbTest {

    private JdbcTemplate jdbcTemplate;
    private TenantBotRepository tenantBotRepository;
    private ResumeContextStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        tenantBotRepository = mock(TenantBotRepository.class);
        store = new ResumeContextStore(tenantBotRepository, jdbcTemplate);
        ReflectionTestUtils.setField(store, "storageMode", "db");
        ReflectionTestUtils.setField(store, "orphanCleanupEnabled", true);
    }

    @Test
    void savesContextAsDbUpsert() {
        store.save(new BotRuntimeKey("tenant-a", "bot-1"), context("token-1"));

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("INSERT INTO ilink_resume_context")
                        && sql.contains("ON DUPLICATE KEY")),
                any(Object[].class));
    }

    @Test
    void loadsContextFromDbPayload() {
        String json = """
                {"loginContext":{"botToken":"tok","userId":"user-1","botId":"bot-1","baseUrl":"https://example.com"},
                 "updatesCursor":"c1","conversationContexts":[]}
                """;
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of(json));

        ResumeContext loaded = store.load(new BotRuntimeKey("tenant-a", "bot-1"));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getLoginContext().getBotToken()).isEqualTo("tok");
    }

    @Test
    void returnsNullWhenNoDbRowAndNoLegacyFile() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        assertThat(store.load(new BotRuntimeKey("tenant-a", "bot-1"))).isNull();
    }

    @Test
    void existsChecksDbRow() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThat(store.exists(new BotRuntimeKey("tenant-a", "bot-1"))).isTrue();
    }

    @Test
    void deletesDbRow() {
        store.delete(new BotRuntimeKey("tenant-a", "bot-1"));

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("DELETE FROM ilink_resume_context")),
                any(Object[].class));
    }

    @Test
    void cleanupOrphansDeletesRowsWithoutActiveBot() {
        TenantBot bot = new TenantBot();
        bot.setTenantId("tenant-a");
        bot.setBotId("bot-1");
        when(tenantBotRepository.findByChannelAndStatus("ILINK", "ACTIVE")).thenReturn(List.of(bot));

        store.cleanupOrphanedFiles();

        verify(jdbcTemplate).update(
                (String) argThat((String sql) -> sql.contains("DELETE c FROM ilink_resume_context")));
    }

    @Test
    void skipsCleanupWhenNoActiveBot() {
        when(tenantBotRepository.findByChannelAndStatus("ILINK", "ACTIVE")).thenReturn(List.of());

        store.cleanupOrphanedFiles();

        verify(jdbcTemplate, never()).update((String) anyString());
    }

    private ResumeContext context(String token) {
        LoginContext login = new LoginContext(token, "user-1", "bot-1", "https://example.com");
        return ResumeContext.builder(login).build();
    }
}
