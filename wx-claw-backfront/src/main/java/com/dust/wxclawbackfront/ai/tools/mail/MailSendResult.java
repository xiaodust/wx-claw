package com.dust.wxclawbackfront.ai.tools.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮件发送结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailSendResult {
    private boolean success;
    private String to;
    private String subject;
    private String sentAt;
    private String errorMsg;
    private String replyText;

    public static MailSendResult success(String to, String subject, String sentAt) {
        MailSendResult result = new MailSendResult();
        result.setSuccess(true);
        result.setTo(to);
        result.setSubject(subject);
        result.setSentAt(sentAt);
        result.setReplyText(String.format("邮件已成功发送到 %s，主题：%s", to, subject));
        return result;
    }

    public static MailSendResult failure(String to, String subject, String errorMsg) {
        MailSendResult result = new MailSendResult();
        result.setSuccess(false);
        result.setTo(to);
        result.setSubject(subject);
        result.setErrorMsg(errorMsg);
        result.setReplyText(String.format("邮件发送失败：%s", errorMsg));
        return result;
    }
}
