package com.dust.wxclawbackfront.ilnk.runtime;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
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

    private volatile ILinkClient activeClient;

    public ILinkRuntimeManager(@Value("${wxclaw.ilink.connect-timeout-ms:15000}") long connectTimeoutMs,
                               @Value("${wxclaw.ilink.read-timeout-ms:35000}") long readTimeoutMs,
                               @Value("${wxclaw.ilink.write-timeout-ms:15000}") long writeTimeoutMs,
                               @Value("${wxclaw.ilink.login-timeout-ms:180000}") long loginTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.loginTimeoutMs = loginTimeoutMs;
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

        ILinkClient client = ILinkClient.builder()
                .config(config)
                .build();

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
        }));
    }
}
