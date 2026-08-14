package com.dust.wxclawbackfront.tenancy.security;

import com.dust.wxclawbackfront.tenancy.service.TenantRegistrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * 公开接口（注册 / 登录）的简易固定窗口限流。
 *
 * <p>注册按 IP 与邮箱分别计数；登录按 用户名+IP 与纯 IP 分别计数，
 * 防止脚本批量注册和密码爆破。限流状态只保存在内存中，单实例部署足够。</p>
 */
@Slf4j
@Component
public class PublicAuthRateLimiter {

    private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maxPerIp;
    private final int maxPerEmail;
    private final int loginPerUserAndIp;
    private final int loginPerIp;
    private final Duration window;

    public PublicAuthRateLimiter(
            @Value("${wxclaw.api.registration.max-per-ip:5}") int maxPerIp,
            @Value("${wxclaw.api.registration.max-per-email:3}") int maxPerEmail,
            @Value("${wxclaw.api.registration.window:PT1H}") Duration window,
            @Value("${wxclaw.api.login.max-per-user-and-ip:10}") int loginPerUserAndIp,
            @Value("${wxclaw.api.login.max-per-ip:30}") int loginPerIp) {
        this.maxPerIp = Math.max(1, maxPerIp);
        this.maxPerEmail = Math.max(1, maxPerEmail);
        this.loginPerUserAndIp = Math.max(1, loginPerUserAndIp);
        this.loginPerIp = Math.max(1, loginPerIp);
        this.window = window == null || window.isZero() || window.isNegative() ? Duration.ofHours(1) : window;
    }

    public void checkRegistration(String clientIp, String contactEmail) {
        checkKey("reg:ip:" + (clientIp == null ? "unknown" : clientIp), maxPerIp);
        if (contactEmail != null && !contactEmail.isBlank()) {
            checkKey("reg:email:" + contactEmail.trim().toLowerCase(), maxPerEmail);
        }
    }

    public void checkLogin(String username, String clientIp) {
        checkKey("login:user+ip:" + (username == null ? "?" : username.toLowerCase())
                + ":" + (clientIp == null ? "unknown" : clientIp), loginPerUserAndIp);
        checkKey("login:ip:" + (clientIp == null ? "unknown" : clientIp), loginPerIp);
    }

    private void checkKey(String key, int max) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> queue = attempts.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.pollFirst();
            }
            if (queue.size() >= max) {
                throw new TenantRegistrationException("RATE_LIMITED",
                        "操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
            }
            queue.addLast(now);
        }
    }

    @Scheduled(fixedDelayString = "${wxclaw.api.registration.cleanup-ms:600000}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(window);
        attempts.forEach((key, queue) -> {
            synchronized (queue) {
                while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                    queue.pollFirst();
                }
            }
            if (queue.isEmpty()) {
                attempts.remove(key, queue);
            }
        });
    }
}
