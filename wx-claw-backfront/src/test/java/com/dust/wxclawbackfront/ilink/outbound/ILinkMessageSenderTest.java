package com.dust.wxclawbackfront.ilink.outbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.github.wechat.ilink.sdk.ILinkClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ILinkMessageSenderTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void routesMessageToCurrentTenantBot() throws Exception {
        ILinkRuntimeManager runtimeManager = mock(ILinkRuntimeManager.class);
        ILinkClient client = mock(ILinkClient.class);
        BotRuntimeKey key = new BotRuntimeKey("tenant-a", "bot-a");
        when(runtimeManager.requireClient(key)).thenReturn(client);
        TenantContextHolder.set(new TenantContext("tenant-a", "ILINK", "bot-a", "user-a", "wx-a",
                Set.of(), Set.of(), "request-a"));

        new ILinkMessageSender(runtimeManager).sendText("wx-a", "hello");

        verify(runtimeManager).requireClient(key);
        verify(client).sendText("wx-a", "hello");
    }
}
