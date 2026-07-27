package com.dust.wxclawbackfront.ilink.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ILinkRuntimeManagerTest {

    @Test
    void doesNotPersistDiscardedResumeContext() {
        ResumeContextStore resumeContextStore = mock(ResumeContextStore.class);
        ILinkClient client = mock(ILinkClient.class);
        ILinkRuntimeManager manager = new ILinkRuntimeManager(1, 1, 1, 1, resumeContextStore,
                new BotRuntimeStatusRegistry());
        BotRuntimeKey key = new BotRuntimeKey("default", "bot-3");

        manager.closeClient(key, client, false);

        verify(client, never()).exportResumeContext();
        verifyNoInteractions(resumeContextStore);
        verify(client).close();
    }

    @Test
    void checkpointsUpdatedCursorWhileRunning() {
        ResumeContextStore resumeContextStore = mock(ResumeContextStore.class);
        ILinkClient client = mock(ILinkClient.class);
        ResumeContext resumeContext = mock(ResumeContext.class);
        ILinkRuntimeManager manager = new ILinkRuntimeManager(1, 1, 1, 1, resumeContextStore,
                new BotRuntimeStatusRegistry());
        BotRuntimeKey key = new BotRuntimeKey("default", "bot-3");
        when(client.isLoggedIn()).thenReturn(true);
        when(client.exportResumeContext()).thenReturn(resumeContext);

        manager.checkpointResumeContext(key, client);

        verify(resumeContextStore).save(key, resumeContext);
    }
}
