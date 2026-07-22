package com.dust.wxclawbackfront.bot.agent.tools.mail;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 邮件工具
 */
@Component
@ConditionalOnBean(MailHandler.class)
public class MailTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 60;
    }

    private final MailHandler mailHandler;
    private final AiToolInvocationStore invocationStore;

    public MailTools(MailHandler mailHandler, AiToolInvocationStore invocationStore) {
        this.mailHandler = mailHandler;
        this.invocationStore = invocationStore;
    }

    @Tool(name = "send_email", 
          description = "发送邮件给指定收件人。可用于发送通知、告警、报告等。支持纯文本和 HTML 格式。")
    public MailToolResult send(String to, String subject, String content, String contentType) {
        boolean isHtml = "html".equalsIgnoreCase(contentType);
        
        MailSendResult result = mailHandler.send(to, subject, content, isHtml);
        
        String args = String.format("to=%s, subject=%s, contentType=%s", to, subject, contentType);
        invocationStore.add("send_email", args, result.getReplyText());
        
        if (result.isSuccess()) {
            return new MailToolResult(true, to, subject, result.getSentAt(), null);
        } else {
            return new MailToolResult(false, to, subject, null, result.getErrorMsg());
        }
    }

    public record MailToolResult(
            boolean success,
            String to,
            String subject,
            String sentAt,
            String errorMsg
    ) {
    }
}
