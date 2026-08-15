package com.dust.wxclawbackfront.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Issues and reads the HttpOnly session cookie used by browser consoles.
 */
@Component
public class SessionCookieService {

    public static final String COOKIE_NAME = "WXCLAW_SESSION";

    private final boolean secure;
    private final String sameSite;

    public SessionCookieService(
            @Value("${wxclaw.security.cookie-secure:false}") boolean secure,
            @Value("${wxclaw.security.cookie-same-site:Lax}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : sameSite;
    }

    public void setSessionCookie(HttpServletResponse response, String token, LocalDateTime expiresAt) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
        if (expiresAt != null) {
            long seconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (seconds > 0) {
                builder.maxAge(seconds);
            }
        }
        response.addHeader("Set-Cookie", builder.build().toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
