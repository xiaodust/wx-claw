package com.dust.wxclawbackfront.ilink.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ILink 运行时管理器
 * 负责 ILinkClient 的生命周期管理：创建、登录、关闭
 */
@Slf4j
@Component
public class ILinkRuntimeManager {

    private final long connectTimeoutMs;
    private final long readTimeoutMs;
    private final long writeTimeoutMs;
    private final long loginTimeoutMs;
    private final ResumeContextStore resumeContextStore;

    private volatile ILinkClient activeClient;

    public ILinkRuntimeManager(@Value("${wxclaw.ilink.connect-timeout-ms:15000}") long connectTimeoutMs,
                               @Value("${wxclaw.ilink.read-timeout-ms:35000}") long readTimeoutMs,
                               @Value("${wxclaw.ilink.write-timeout-ms:15000}") long writeTimeoutMs,
                               @Value("${wxclaw.ilink.login-timeout-ms:180000}") long loginTimeoutMs,
                               ResumeContextStore resumeContextStore) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.loginTimeoutMs = loginTimeoutMs;
        this.resumeContextStore = resumeContextStore;
    }

    /**
     * 获取当前活跃的 ILinkClient
     */
    public ILinkClient getActiveClient() {
        return activeClient;
    }

    /**
     * 创建并登录 ILinkClient
     */
    public ILinkClient createAndLogin() throws Exception {
        ILinkConfig config = ILinkConfig.builder()
                .connectTimeoutMs(connectTimeoutMs)
                .readTimeoutMs(readTimeoutMs)
                .writeTimeoutMs(writeTimeoutMs)
                .loginTimeoutMs(loginTimeoutMs)
                .heartbeatEnabled(false)
                .build();

        // 尝试加载上次保存的 ResumeContext，恢复登录上下文和所有用户的 context token
        ResumeContext resumeContext = resumeContextStore.load();

        ILinkClient client;
        if (resumeContext != null && resumeContext.getLoginContext() != null) {
            log.info("检测到 ResumeContext，尝试恢复登录状态（包括 {} 个用户的 context token）", 
                    resumeContext.getConversationContexts().size());
            
            client = ILinkClient.builder()
                    .config(config)
                    .resumeContext(resumeContext)
                    .build();

            // 如果恢复成功（已登录），直接返回
            if (client.isLoggedIn()) {
                this.activeClient = client;
                log.info("iLink 登录状态已恢复，无需重新扫码");
                return client;
            } else {
                log.warn("ResumeContext 恢复失败或登录已过期，需要重新扫码登录");
            }
        } else {
            client = ILinkClient.builder()
                    .config(config)
                    .build();
        }

        this.activeClient = client;

        String qrCodeContent = client.executeLogin();
        log.info("请扫码登录：\n{}", qrCodeContent);
        client.getLoginFuture().get();
        log.info("iLink 登录成功");

        return client;
    }

    /**
     * 关闭 ILinkClient
     */
    public void closeClient(ILinkClient client) {
        if (client != null) {
            try {
                // 在关闭前导出当前状态，以便下次启动时恢复
                ResumeContext context = client.exportResumeContext();
                if (context != null) {
                    resumeContextStore.save(context);
                    log.info("已保存 ResumeContext（包括 {} 个用户的 context token）", 
                            context.getConversationContexts().size());
                }
                
                client.close();
            } catch (Exception e) {
                log.warn("关闭 ILinkClient 失败: {}", e.getMessage());
            }
        }
        this.activeClient = null;
    }

    /**
     * 注册关闭 hook
     */
    public void registerShutdownHook(ILinkClient client, AtomicBoolean stopFlag) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopFlag.set(true);
            closeClient(client);
        }, "ilink-shutdown-hook"));
    }

    /**
     * 删除持久化的 ResumeContext（通常在登录过期时调用）
     */
    public void deleteResumeContext() {
        resumeContextStore.delete();
    }
}
