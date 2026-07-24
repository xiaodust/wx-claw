package com.dust.wxclawbackfront.ilink.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ILinkRuntimeManagerTest {

    @Test
    void doesNotPersistDiscardedResumeContext() {
        ResumeContextStore resumeContextStore = mock(ResumeContextStore.class);
        ILinkClient client = mock(ILinkClient.class);
        ILinkRuntimeManager manager = new ILinkRuntimeManager(1, 1, 1, 1, resumeContextStore);
        BotRuntimeKey key = new BotRuntimeKey("default", "bot-3");

        manager.closeClient(key, client, false);

        verify(client, never()).exportResumeContext();
        verifyNoInteractions(resumeContextStore);
        verify(client).close();
    }
}
