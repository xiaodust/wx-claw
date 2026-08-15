package com.dust.wxclawbackfront.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates URLs that the server may fetch on behalf of a tenant.
 *
 * <p>The default policy is strict: public hostnames only, HTTPS for API
 * endpoints, and no private, loopback, link-local, or metadata addresses.</p>
 */
@Component
public class UrlSafetyValidator {

    private final boolean allowPrivateAddresses;

    public UrlSafetyValidator(
            @Value("${wxclaw.security.allow-private-url:false}") boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    /**
     * Validates a tenant-supplied LLM-compatible API base URL.
     */
    public void validateCustomBaseUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("自定义服务商 baseUrl 必须使用 HTTPS");
        }
        validateHost(uri.getHost());
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("自定义服务商 baseUrl 不能包含用户信息");
        }
    }

    /**
     * Validates a server-side fetch URL. HTTP is allowed for compatibility,
     * but the destination must still be a public address.
     */
    public URI validatePublicFetchUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅支持 HTTP/HTTPS 地址");
        }
        validateHost(uri.getHost());
        return uri;
    }

    private URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("URL 缺少有效主机名");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("URL 格式无效", ex);
        }
    }

    private void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少有效主机名");
        }
        if (looksLikeIpLiteral(host)) {
            throw new IllegalArgumentException("禁止直接使用 IP 地址");
        }
        if (allowPrivateAddresses) {
            return;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("无法解析 URL 主机名");
            }
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("URL 目标地址不在允许范围内");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法验证 URL 目标地址", ex);
        }
    }

    private boolean looksLikeIpLiteral(String host) {
        if (host == null) {
            return false;
        }
        String value = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        if (value.matches("\\d+(\\.\\d+){3}")) {
            return true;
        }
        return value.contains(":");
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes != null && bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
            // IPv6 unique-local range fc00::/7
            return true;
        }
        return false;
    }
}
