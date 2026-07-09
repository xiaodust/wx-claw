package com.dust.wxclawbackfront.ilnk.outbound;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.dust.wxclawbackfront.ilnk.runtime.ILinkRuntimeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ILink 消息发送器
 * 统一封装所有向用户发送消息的逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageSender {

    private final ILinkRuntimeManager runtimeManager;

    /**
     * 发送文本消息
     */
    public void sendText(String userId, String text) throws Exception {
        ILinkClient client = runtimeManager.getActiveClient();
        if (client == null) {
            throw new IllegalStateException("ILinkClient 未初始化");
        }
        client.sendText(userId, text);
        log.debug("发送文本消息成功: userId={}", userId);
    }

    /**
     * 发送图片消息
     */
    public void sendImage(String userId, byte[] imageBytes, String fileName, String text) throws Exception {
        ILinkClient client = runtimeManager.getActiveClient();
        if (client == null) {
            throw new IllegalStateException("ILinkClient 未初始化");
        }
        client.sendImage(userId, imageBytes, fileName, text);
        log.debug("发送图片消息成功: userId={}, fileName={}", userId, fileName);
    }

    /**
     * 发送文件消息
     */
    public void sendFile(String userId, byte[] fileBytes, String fileName, String text) throws Exception {
        ILinkClient client = runtimeManager.getActiveClient();
        if (client == null) {
            throw new IllegalStateException("ILinkClient 未初始化");
        }
        client.sendFile(userId, fileBytes, fileName, text);
        log.debug("发送文件消息成功: userId={}, fileName={}", userId, fileName);
    }
}
