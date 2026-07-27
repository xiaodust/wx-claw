package com.dust.wxclawbackfront.ilink.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.dust.wxclawbackfront.ilink.runtime.status.BotRuntimeStatusRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ILinkRuntimeManager {
    private final long connectTimeoutMs;
    private final long readTimeoutMs;
    private final long writeTimeoutMs;
    private final long loginTimeoutMs;
    private final ResumeContextStore resumeContextStore;
    private final BotRuntimeStatusRegistry statusRegistry;
    private final Map<BotRuntimeKey, ILinkRuntime> runtimes = new ConcurrentHashMap<>();

    public ILinkRuntimeManager(@Value("${wxclaw.ilink.connect-timeout-ms:15000}") long connectTimeoutMs,
                               @Value("${wxclaw.ilink.read-timeout-ms:35000}") long readTimeoutMs,
                               @Value("${wxclaw.ilink.write-timeout-ms:15000}") long writeTimeoutMs,
                               @Value("${wxclaw.ilink.login-timeout-ms:180000}") long loginTimeoutMs,
                               ResumeContextStore resumeContextStore,
                               BotRuntimeStatusRegistry statusRegistry) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.loginTimeoutMs = loginTimeoutMs;
        this.resumeContextStore = resumeContextStore;
        this.statusRegistry = statusRegistry;
    }

    public ILinkClient createAndLogin(BotRuntimeKey key) throws Exception {
        ILinkConfig config = ILinkConfig.builder().connectTimeoutMs(connectTimeoutMs).readTimeoutMs(readTimeoutMs)
                .writeTimeoutMs(writeTimeoutMs).loginTimeoutMs(loginTimeoutMs).heartbeatEnabled(false).build();
        ResumeContext resumeContext = resumeContextStore.load(key);
        statusRegistry.starting(key, resumeContext != null && resumeContext.getLoginContext() != null);
        ILinkClient client = resumeContext == null || resumeContext.getLoginContext() == null
                ? ILinkClient.builder().config(config).build()
                : ILinkClient.builder().config(config).resumeContext(resumeContext).build();
        try {
            if (!client.isLoggedIn()) {
                String qrCodeContent = client.executeLogin();
                statusRegistry.waitingForQr(key);
                log.info("Bot {} / {} 请扫码登录：\n{}", key.tenantId(), key.botId(), qrCodeContent);
                client.getLoginFuture().get();
            }
        } catch (Exception ex) {
            try {
                client.close();
            } catch (Exception closeEx) {
                log.warn("关闭登录失败的 ILink Bot 失败: tenantId={}, botId={}, error={}",
                        key.tenantId(), key.botId(), closeEx.getMessage());
            }
            throw ex;
        }
        runtimes.put(key, new ILinkRuntime(key, client, Instant.now()));
        statusRegistry.loginSucceeded(key, resumeContextStore.exists(key));
        log.info("ILink Bot 已连接: tenantId={}, botId={}", key.tenantId(), key.botId());
        return client;
    }

    public ILinkClient requireClient(BotRuntimeKey key) {
        ILinkRuntime runtime = runtimes.get(key);
        if (runtime == null || runtime.client() == null) {
            throw new IllegalStateException("ILinkClient is not connected for " + key);
        }
        return runtime.client();
    }

    public void closeClient(BotRuntimeKey key, ILinkClient client) {
        closeClient(key, client, true);
    }

    public void checkpointResumeContext(BotRuntimeKey key, ILinkClient client) {
        if (client == null) {
            return;
        }
        try {
            if (!client.isLoggedIn()) {
                return;
            }
            ResumeContext context = client.exportResumeContext();
            if (context != null) {
                resumeContextStore.save(key, context);
            }
        } catch (Exception ex) {
            log.warn("保存 iLink 消费游标失败: tenantId={}, botId={}, error={}",
                    key.tenantId(), key.botId(), ex.getMessage());
        }
    }

    public void closeClient(BotRuntimeKey key, ILinkClient client, boolean saveResumeContext) {
        runtimes.remove(key);
        if (client == null) return;
        try {
            if (saveResumeContext) {
                checkpointResumeContext(key, client);
            }
            client.close();
        } catch (Exception ex) {
            log.warn("关闭 ILink Bot 失败: tenantId={}, botId={}, error={}", key.tenantId(), key.botId(), ex.getMessage());
        }
    }

    public void deleteResumeContext(BotRuntimeKey key) {
        resumeContextStore.delete(key);
    }

    @PreDestroy
    public void closeAll() {
        runtimes.forEach((key, runtime) -> closeClient(key, runtime.client()));
    }
}
