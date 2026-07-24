package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import com.dust.wxclawbackfront.ilink.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ILinkBotServiceTest {

    @Test
    void detectsWrappedExpiredQrCode() {
        Exception expired = new ExecutionException(new RuntimeException("qrcode expired"));

        assertTrue(ILinkBotService.isQrCodeExpired(expired));
        assertFalse(ILinkBotService.isQrCodeExpired(new RuntimeException("connection refused")));
    }

    @Test
    void fallsBackToQrLoginAfterConsecutiveSessionExpirationLimit() throws Exception {
        ILinkRuntimeManager runtimeManager = mock(ILinkRuntimeManager.class);
        ILinkMessageDispatcher messageDispatcher = mock(ILinkMessageDispatcher.class);
        DynamicTaskSchedulerService schedulerService = mock(DynamicTaskSchedulerService.class);
        TenantBotRepository tenantBotRepository = mock(TenantBotRepository.class);
        ExecutorService messageExecutor = mock(ExecutorService.class);
        ExecutorService runtimeExecutor = mock(ExecutorService.class);
        ILinkClient expiredClient = mock(ILinkClient.class);
        ILinkClient qrLoginClient = mock(ILinkClient.class);
        SessionExpiredException sessionExpired = mock(SessionExpiredException.class);
        BotRuntimeKey key = new BotRuntimeKey("default", "bot-3");
        AtomicReference<ILinkBotService> serviceReference = new AtomicReference<>();

        ILinkBotService service = new ILinkBotService(runtimeManager, messageDispatcher, schedulerService,
                tenantBotRepository, messageExecutor, runtimeExecutor);
        serviceReference.set(service);
        ReflectionTestUtils.setField(service, "maxReconnectAttempts", 2);
        ReflectionTestUtils.setField(service, "reconnectDelaySeconds", 0);
        ReflectionTestUtils.setField(service, "pollIdleMs", 0L);

        when(expiredClient.getUpdates()).thenThrow(sessionExpired);
        when(runtimeManager.createAndLogin(key))
                .thenReturn(expiredClient, expiredClient)
                .thenAnswer(invocation -> {
                    serviceReference.get().stopAllBots();
                    return qrLoginClient;
                });

        service.runILinkMonitor(key);

        verify(runtimeManager).closeClient(key, expiredClient, false);
        verify(runtimeManager).deleteResumeContext(key);
        verify(runtimeManager, times(3)).createAndLogin(key);
        verify(runtimeManager, never()).closeClient(key, qrLoginClient, false);
    }
}
