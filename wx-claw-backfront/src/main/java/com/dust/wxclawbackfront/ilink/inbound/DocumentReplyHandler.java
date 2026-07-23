package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.agent.llm.chat.document.DocumentGenerator;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文档回复处理器
 * 处理长文本回复，自动生成文档发送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReplyHandler {

    private final DocumentGenerator documentGenerator;
    private final ILinkMessageSender messageSender;

    /**
     * 发送回复（如果是长文本则自动转为文档）
     * @return true 如果已发送（文本或文档），false 如果 reply 为空
     * @throws Exception 发送失败时抛出
     */
    public boolean sendReply(String userId, String reply, String userText) throws Exception {
        if (reply == null || reply.isBlank()) {
            return false;
        }

        if (documentGenerator.shouldGenerateDocument(reply)) {
            sendAsDocument(userId, reply, userText);
        } else {
            messageSender.sendText(userId, reply);
        }
        return true;
    }

    /**
     * 将长文本作为文档发送
     * @throws Exception 发送失败时抛出
     */
    private void sendAsDocument(String userId, String reply, String userText) throws Exception {
        String format = isMarkdownRequested(userText) ? "markdown" : "txt";
        DocumentGenerator.DocumentResult docResult = documentGenerator.generate(reply, format);

        if (docResult.isSuccess()) {
            messageSender.sendFile(userId, docResult.bytes(), docResult.fileName(), "内容较长，已生成文档，请查收。");
        } else {
            // 降级为文本发送
            messageSender.sendText(userId, reply);
        }
    }

    /**
     * 判断用户是否请求 markdown 格式的文档
     */
    private boolean isMarkdownRequested(String userText) {
        if (userText == null) {
            return false;
        }
        String text = userText.toLowerCase();
        return text.contains("markdown") || text.contains("md格式") || text.contains("md文档")
                || text.contains("markdown格式") || text.contains("markdown文档")
                || text.contains("返回md") || text.contains("返回markdown");
    }
}
