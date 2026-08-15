package com.dust.wxclawbackfront.bot.agent.tools.mail;

import com.dust.wxclawbackfront.config.security.TenantAiKeyCipher;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantMailConfig;
import com.dust.wxclawbackfront.user.service.TenantMailConfigService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TenantMailSender {

    private final TenantMailConfigService mailConfigService;
    private final TenantAiKeyCipher keyCipher;
    private final List<String> whitelist;
    private final int rateLimit;
    private final ConcurrentHashMap<String, AtomicLong> rateLimitMap = new ConcurrentHashMap<>();

    public TenantMailSender(TenantMailConfigService mailConfigService,
                            TenantAiKeyCipher keyCipher,
                            @Value("${wxclaw.mail.whitelist:}") List<String> whitelist,
                            @Value("${wxclaw.mail.rate-limit:5}") int rateLimit) {
        this.mailConfigService = mailConfigService;
        this.keyCipher = keyCipher;
        this.whitelist = whitelist == null || whitelist.isEmpty() ? List.of("*") : whitelist;
        this.rateLimit = rateLimit;
    }

    public MailSendResult send(String to, String subject, String content, boolean isHtml) {
        if (to == null || to.isBlank()) {
            return MailSendResult.failure(to, subject, "收件人邮箱不能为空");
        }
        if (subject == null || subject.isBlank()) {
            return MailSendResult.failure(to, subject, "邮件主题不能为空");
        }
        if (content == null || content.isBlank()) {
            return MailSendResult.failure(to, subject, "邮件内容不能为空");
        }
        if (!isAllowed(to)) {
            return MailSendResult.failure(to, subject, "收件人邮箱不在白名单中");
        }
        if (!checkRateLimit()) {
            return MailSendResult.failure(to, subject, "发送邮件过于频繁，请稍后再试");
        }
        String tenantId = TenantContextHolder.require().tenantId();
        TenantMailConfig config = mailConfigService.loadForTenant(tenantId);
        if (config == null) {
            return MailSendResult.failure(to, subject, "请先在用户控制台配置发件邮箱");
        }
        String password = keyCipher.decrypt(config.getPasswordCipher());
        if (password == null || password.isBlank()) {
            return MailSendResult.failure(to, subject, "发件邮箱授权码无效");
        }
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(config.getSmtpHost());
            sender.setPort(config.getSmtpPort());
            sender.setUsername(config.getUsername());
            sender.setPassword(password);
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.timeout", "10000");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(config.getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);
            sender.send(message);

            String sentAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return MailSendResult.success(to, subject, sentAt);
        } catch (Exception ex) {
            return MailSendResult.failure(to, subject, "发送失败：" + ex.getMessage());
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
