package com.dust.wxclawbackfront.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionCleanupTest {

    @Test
    void cleansExpiredMemoryChunksAndUserProfiles() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataRetentionCleanup cleanup = new DataRetentionCleanup(jdbcTemplate);
        ReflectionTestUtils.setField(cleanup, "memoryChunksEnabled", true);
        ReflectionTestUtils.setField(cleanup, "userProfilesEnabled", true);
        when(jdbcTemplate.update(anyString(), any(PreparedStatementSetter.class))).thenReturn(5, 0, 3, 0);

        cleanup.cleanupExpiredMemoryChunks();
        cleanup.cleanupExpiredUserProfiles();

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("conversation_memory_chunk") && sql.contains("expires_at")),
                any(PreparedStatementSetter.class));
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("user_profile") && sql.contains("expires_at")),
                any(PreparedStatementSetter.class));
    }
}
