package com.dust.wxclawbackfront.ilink.outbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.dust.wxclawbackfront.ilink.runtime.ILinkRuntimeManager;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.github.wechat.ilink.sdk.ILinkClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageSender {
    private final ILinkRuntimeManager runtimeManager;

    public void sendText(String userId, String text) throws Exception {
        currentClient().sendText(userId, text);
        log.debug("发送文本消息成功: userId={}", userId);
    }

    public void sendImage(String userId, byte[] imageBytes, String fileName, String text) throws Exception {
        currentClient().sendImage(userId, imageBytes, fileName, text);
        log.debug("发送图片消息成功: userId={}, fileName={}", userId, fileName);
    }

    public void sendFile(String userId, byte[] fileBytes, String fileName, String text) throws Exception {
        currentClient().sendFile(userId, fileBytes, fileName, text);
        log.debug("发送文件消息成功: userId={}, fileName={}", userId, fileName);
    }

    public void sendVideo(String userId, byte[] videoBytes, String fileName, Integer playLengthMs, String caption) throws Exception {
        currentClient().sendVideo(userId, videoBytes, fileName, playLengthMs, caption);
        log.debug("发送视频消息成功: userId={}, fileName={}", userId, fileName);
    }

    private ILinkClient currentClient() {
        TenantContext context = TenantContextHolder.require();
        if (context.botId() == null || context.botId().isBlank()) {
            throw new IllegalStateException("Bot ID is required to send an ILink message");
        }
        return runtimeManager.requireClient(new BotRuntimeKey(context.tenantId(), context.botId()));
    }
}
