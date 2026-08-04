package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ILinkMessageReceiptStoreTest {
    @Test
    void claimsEachBotMessageOnlyOnce() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ILinkMessageReceiptStore store = new ILinkMessageReceiptStore(jdbcTemplate);
        BotRuntimeKey runtimeKey = new BotRuntimeKey("tenant-a", "bot-1");
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setFrom_user_id("user-1");
        message.setCreate_time_ms(456L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertTrue(store.claim(runtimeKey, message));
        assertFalse(store.claim(runtimeKey, message));

        verify(jdbcTemplate, times(2)).update(
                argThat(sql -> sql.contains("INSERT IGNORE INTO")), any(Object[].class));
    }

    @Test
    void acceptsMessagesWithoutSdkIdWithoutPersisting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ILinkMessageReceiptStore store = new ILinkMessageReceiptStore(jdbcTemplate);

        assertTrue(store.claim(new BotRuntimeKey("tenant-a", "bot-1"), new WeixinMessage()));

        verify(jdbcTemplate, times(0)).update(anyString(), any(Object[].class));
    }

    @Test
    void deletesReceiptsOlderThanCutoff() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ILinkMessageReceiptStore store = new ILinkMessageReceiptStore(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(1000, 5);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long deleted = store.deleteOlderThan(cutoff);

        assertEquals(1005, deleted);
        verify(jdbcTemplate, times(2)).update(
                argThat(sql -> sql.startsWith("DELETE FROM ilink_message_receipt") && sql.contains("LIMIT")),
                any(PreparedStatementSetter.class));
    }
}
