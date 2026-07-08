package com.dust.wxclawbackfront.ilnk.media;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class WechatCdnClient {

    private final HttpClient httpClient;
    private final String cdnBaseUrl;

    public WechatCdnClient(String cdnBaseUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.cdnBaseUrl = (cdnBaseUrl == null || cdnBaseUrl.isBlank())
                ? "https://novac2c.cdn.weixin.qq.com/c2c"
                : cdnBaseUrl.trim();
    }

    public byte[] downloadEncrypted(String encryptQueryParam) {
        if (encryptQueryParam == null || encryptQueryParam.isBlank()) {
            throw new IllegalArgumentException("encryptQueryParam is blank");
        }
        String url = buildDownloadUrl(encryptQueryParam.trim());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("cdn http status: " + resp.statusCode());
            }
            return resp.body();
        } catch (Exception ex) {
            throw new IllegalStateException("cdn download failed: " + ex.getMessage(), ex);
        }
    }

    private String buildDownloadUrl(String encryptQueryParam) {
        if (encryptQueryParam.contains("encrypted_query_param=")) {
            return cdnBaseUrl + "/download?" + encryptQueryParam;
        }
        return cdnBaseUrl + "/download?encrypted_query_param=" + URLEncoder.encode(encryptQueryParam, StandardCharsets.UTF_8);
    }
}
