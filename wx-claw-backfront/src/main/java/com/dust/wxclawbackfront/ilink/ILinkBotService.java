package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.ilink.inbound.ILinkMessageDispatcher;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.bot.scheduler.DynamicTaskSchedulerService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
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
    private final DynamicTaskSchedulerService taskSchedulerService;
    @Qualifier("messageProcessingExecutor")
    private final ExecutorService messageProcessingExecutor;

    @Value("${wxclaw.ilink.poll-idle-ms:200}")
    private long pollIdleMs;

    @Value("${wxclaw.ilink.reconnect.max-attempts:5}")
    private int maxReconnectAttempts;

    @Value("${wxclaw.ilink.reconnect.delay-seconds:30}")
    private int reconnectDelaySeconds;

    /**
     * 运行 ILink 监听服务
     */
    public void runILinkMonitor() {
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        int reconnectAttempts = 0;

        while (reconnectAttempts < maxReconnectAttempts && !stopFlag.get()) {
            ILinkClient client = null;
            try {
                client = runtimeManager.createAndLogin();
                reconnectAttempts = 0;  // 登录成功重置计数

                runtimeManager.registerShutdownHook(client, stopFlag);

                // 连接就绪后，补偿执行登录前已到期但因未连接而未发送的一次性任务
                try {
                    taskSchedulerService.runOverdueOnceTasks();
                } catch (Exception ex) {
                    log.error("补偿执行过期任务失败: {}", ex.getMessage(), ex);
                }

                log.info("开始监听消息...");

                while (!stopFlag.get()) {
                    try {
                        List<WeixinMessage> messages = client.getUpdates();
                        if (messages != null) {
                            for (WeixinMessage msg : messages) {
                                // 异步处理消息，避免阻塞消息拉取
                                messageProcessingExecutor.submit(() -> {
                                    try {
                                        messageDispatcher.dispatch(msg);
                                    } catch (Exception e) {
                                        log.error("消息处理异常: {}", e.getMessage(), e);
                                    }
                                });
                            }
                        }
                    } catch (SessionExpiredException ex) {
                        log.warn("登录会话已过期，尝试重连 {}/{}: {}",
                                reconnectAttempts + 1, maxReconnectAttempts, ex.getMessage());
                        runtimeManager.deleteResumeContext();
                        reconnectAttempts++;
                        break;
                    } catch (Exception ex) {
                        log.warn("监听错误: {}", ex.getMessage());
                        sleepQuietly(1000L);
                    }
                    sleepQuietly(pollIdleMs);
                }

            } catch (Exception ex) {
                log.error("iLink 启动失败: {}", ex.getMessage(), ex);
                reconnectAttempts++;
                if (reconnectAttempts < maxReconnectAttempts) {
                    log.info("等待 {} 秒后重试...", reconnectDelaySeconds);
                    sleepQuietly(reconnectDelaySeconds * 1000L);
                }
            } finally {
                runtimeManager.closeClient(client);
            }
        }

        if (reconnectAttempts >= maxReconnectAttempts) {
            log.error("重连次数耗尽（{}次），服务停止", maxReconnectAttempts);
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
