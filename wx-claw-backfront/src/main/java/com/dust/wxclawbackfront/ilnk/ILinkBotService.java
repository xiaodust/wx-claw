package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ilnk.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilnk.runtime.ILinkRuntimeManager;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ILink Bot 服务
 * 只负责编排：启动运行时、轮询消息、委托 dispatcher 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkBotService {

    private final ILinkRuntimeManager runtimeManager;
    private final ILinkMessageDispatcher messageDispatcher;

    @Value("${wxclaw.ilink.poll-idle-ms:200}")
    private long pollIdleMs;

    /**
     * 运行 ILink 监听服务
     */
    public void runILinkMonitor() {
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        ILinkClient client = null;

        try {
            client = runtimeManager.createAndLogin();
            runtimeManager.registerShutdownHook(client, stopFlag);

            log.info("开始监听消息...");

            while (!stopFlag.get()) {
                try {
                    List<WeixinMessage> messages = client.getUpdates();
                    if (messages != null) {
                        for (WeixinMessage msg : messages) {
                            messageDispatcher.dispatch(msg);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("监听错误: {}", ex.getMessage());
                    sleepQuietly(1000L);
                }
                sleepQuietly(pollIdleMs);
            }

        } catch (Exception ex) {
            log.error("iLink 启动失败: {}", ex.getMessage(), ex);
        } finally {
            runtimeManager.closeClient(client);
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
