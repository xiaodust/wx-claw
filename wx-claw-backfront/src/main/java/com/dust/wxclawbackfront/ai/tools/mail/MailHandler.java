package com.dust.wxclawbackfront.ai.tools.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 邮件发送处理器
 */
@Service
@ConditionalOnProperty(name = "wxclaw.mail.enabled", havingValue = "true", matchIfMissing = false)
public class MailHandler {

    private final JavaMailSender mailSender;
    private final String from;
    private final List<String> whitelist;
    private final int rateLimit;
    private final ConcurrentHashMap<String, AtomicLong> rateLimitMap = new ConcurrentHashMap<>();

    public MailHandler(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String from,
                       @Value("${wxclaw.mail.whitelist:}") List<String> whitelist,
                       @Value("${wxclaw.mail.rate-limit:5}") int rateLimit) {
        this.mailSender = mailSender;
        this.from = from;
        this.whitelist = whitelist == null || whitelist.isEmpty() ? List.of("*") : whitelist;
        this.rateLimit = rateLimit;
    }

    public MailSendResult send(String to, String subject, String content, boolean isHtml) {
        try {
            if (to == null || to.isBlank()) {
                return MailSendResult.failure(to, subject, "收件人邮箱不能为空");
            }
            if (subject == null || subject.isBlank()) {
                return MailSendResult.failure(to, subject, "邮件主题不能为空");
            }
            if (content == null || content.isBlank()) {
                return MailSendResult.failure(to, subject, "邮件内容不能为空");
            }

            if (from == null || from.isBlank()) {
                return MailSendResult.failure(to, subject, "发件人邮箱未配置，请检查 spring.mail.username");
            }

            if (!isAllowed(to)) {
                return MailSendResult.failure(to, subject, "收件人邮箱不在白名单中");
            }

            if (!checkRateLimit()) {
                return MailSendResult.failure(to, subject, "发送邮件过于频繁，请稍后再试");
            }

            if (content.length() > 50000) {
                return MailSendResult.failure(to, subject, "邮件内容不能超过 50000 字符");
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);

            mailSender.send(message);

            String sentAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return MailSendResult.success(to, subject, sentAt);

        } catch (Exception e) {
            return MailSendResult.failure(to, subject, "发送失败：" + e.getMessage());
        }
    }

    private boolean isAllowed(String email) {
        if (whitelist.contains("*")) {
            return true;
        }
        for (String pattern : whitelist) {
            if (pattern.equals(email)) {
                return true;
            }
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (email.matches(regex)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkRateLimit() {
        long currentSecond = System.currentTimeMillis() / 1000;
        AtomicLong counter = rateLimitMap.computeIfAbsent(String.valueOf(currentSecond), k -> new AtomicLong(0));
        
        rateLimitMap.keySet().removeIf(key -> {
            try {
                return Long.parseLong(key) < currentSecond - 10;
            } catch (NumberFormatException e) {
                return false;
            }
        });

        return counter.incrementAndGet() <= rateLimit;
    }
}
