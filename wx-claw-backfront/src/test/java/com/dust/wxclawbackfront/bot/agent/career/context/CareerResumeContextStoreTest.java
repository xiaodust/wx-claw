package com.dust.wxclawbackfront.bot.agent.career.context;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CareerResumeContextStoreTest {
    private final JobHelperProperties properties = properties();
    private final JobHelperMcpClient client = client();
    private final CareerResumeContextStore store = new CareerResumeContextStore(properties, client);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void isolatesResumeByTenantAndBotAndCopiesBytes() {
        byte[] source = pdf("tenant-a");
        useContext("tenant-a", "bot-a", "user-a");
        assertTrue(store.storeCurrent("resume.pdf", source).stored());
        source[5] = 'X';

        byte[] stored = store.getCurrent().orElseThrow().fileBytes();
        assertArrayEquals(pdf("tenant-a"), stored);
        stored[5] = 'Y';
        assertArrayEquals(pdf("tenant-a"), store.getCurrent().orElseThrow().fileBytes());

        useContext("tenant-b", "bot-a", "user-a");
        assertTrue(store.getCurrent().isEmpty());
        useContext("tenant-a", "bot-b", "user-a");
        assertTrue(store.getCurrent().isEmpty());
    }

    @Test
    void rejectsNonPdfAndSupportsExplicitClear() {
        useContext("tenant-a", "bot-a", "user-a");
        assertFalse(store.storeCurrent("resume.txt", "hello".getBytes()).stored());
        assertTrue(store.storeCurrent("resume.pdf", pdf("ok")).stored());
        assertTrue(store.clearCurrent());
        assertTrue(store.getCurrent().isEmpty());
    }

    private JobHelperProperties properties() {
        JobHelperProperties value = new JobHelperProperties();
        value.setEnabled(true);
        value.setMaxResumeSize(DataSize.ofMegabytes(10));
        value.setResumeContextTtl(Duration.ofMinutes(30));
        return value;
    }

    private JobHelperMcpClient client() {
        JobHelperMcpClient value = mock(JobHelperMcpClient.class);
        when(value.currentResume(any())).thenReturn(new JobHelperDtos.CurrentResume(false, null, null));
        return value;
    }

    private void useContext(String tenantId, String botId, String userId) {
        TenantContextHolder.set(new TenantContext(tenantId, "ILINK", botId, userId, userId,
                Set.of(), Set.of(), "request-1"));
    }

    private byte[] pdf(String content) {
        return ("%PDF-" + content).getBytes();
    }
}
