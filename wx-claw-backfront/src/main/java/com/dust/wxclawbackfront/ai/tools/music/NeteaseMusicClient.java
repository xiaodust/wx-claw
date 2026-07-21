package com.dust.wxclawbackfront.ai.tools.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 网易云音乐开放平台客户端
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wxclaw.music.enabled", havingValue = "true")
public class NeteaseMusicClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String appId;
    private final String appSecret;
    private final String privateKey;
    private final String deviceId;
    private final String deviceType;
    private final String deviceOs;
    private final String deviceChannel;
    private final String deviceBrand;
    private final Duration timeout;
    private final Path tokenStorePath;

    // Token 状态（使用 volatile 保证多线程可见性）
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant tokenExpireTime;
    private final ReentrantReadWriteLock tokenLock = new ReentrantReadWriteLock();

    public NeteaseMusicClient(ObjectMapper objectMapper,
                               @Value("${wxclaw.music.base-url:http://openapi.music.163.com}") String baseUrl,
                               @Value("${wxclaw.music.app-id:}") String appId,
                               @Value("${wxclaw.music.app-secret:}") String appSecret,
                               @Value("${wxclaw.music.private-key:}") String privateKey,
                               @Value("${wxclaw.music.access-token:}") String accessToken,
                               @Value("${wxclaw.music.refresh-token:}") String refreshToken,
                               @Value("${wxclaw.music.device-id:openapi}") String deviceId,
                               @Value("${wxclaw.music.device-type:openapi}") String deviceType,
                               @Value("${wxclaw.music.device-os:openapi}") String deviceOs,
                               @Value("${wxclaw.music.device-channel:openapi}") String deviceChannel,
                               @Value("${wxclaw.music.device-brand:openapi}") String deviceBrand,
                               @Value("${wxclaw.music.timeout:PT10S}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.appId = appId;
        this.appSecret = appSecret;
        this.privateKey = privateKey;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.deviceOs = deviceOs;
        this.deviceChannel = deviceChannel;
        this.deviceBrand = deviceBrand;
        this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        this.tokenStorePath = Path.of("data", "netease-music-token.json");

        // 尝试从文件恢复 token
        loadTokenFromFile();
    }

    /**
     * 通过 grantCode 换取 accessToken
     *
     * @param grantCode 授权码（10分钟有效期）
     * @return 是否成功
     */
    public boolean exchangeToken(String grantCode) {
        String bizContent = String.format("{\"grantCode\":\"%s\"}", grantCode);
        String response = doTokenRequest("/openapi/music/basic/user/oauth2/token/get/v2", bizContent);

        if (response == null) return false;

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                log.error("换取accessToken失败: code={}, message={}", code, root.path("message").asText());
                return false;
            }

            JsonNode data = root.path("data");
            String newAccessToken = data.path("accessToken").asText(null);
            String newRefreshToken = data.path("refreshToken").asText(null);
            long expireIn = data.path("expireIn").asLong(604800);

            if (newAccessToken != null) {
                updateToken(newAccessToken, newRefreshToken, expireIn);
                log.info("成功换取accessToken，有效期: {}秒", expireIn);
                return true;
            }

        } catch (Exception ex) {
            log.error("解析token响应失败: {}", ex.getMessage());
        }

        return false;
    }

    /**
     * 通过 refreshToken 刷新 accessToken
     *
     * @return 是否成功
     */
    public boolean refreshAccessToken() {
        tokenLock.readLock().lock();
        String currentRefreshToken = this.refreshToken;
        tokenLock.readLock().unlock();

        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            log.warn("没有refreshToken，无法刷新accessToken");
            return false;
        }

        String bizContent = String.format("{\"clientId\":\"%s\",\"clientSecret\":\"%s\",\"refreshToken\":\"%s\"}",
                appId, appSecret, currentRefreshToken);

        String response = doTokenRequest("/openapi/music/basic/user/oauth2/token/refresh/v2", bizContent);

        if (response == null) return false;

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                log.error("刷新accessToken失败: code={}, message={}", code, root.path("message").asText());
                // refreshToken 失效，清除 token
                if (code == 1406) {
                    clearToken();
                }
                return false;
            }

            JsonNode data = root.path("data");
            String newAccessToken = data.path("accessToken").asText(null);
            String newRefreshToken = data.path("refreshToken").asText(null);
            long expiresTime = data.path("expiresTime").asLong(604800);

            if (newAccessToken != null) {
                updateToken(newAccessToken, newRefreshToken, expiresTime);
                log.info("成功刷新accessToken，有效期: {}秒", expiresTime);
                return true;
            }

        } catch (Exception ex) {
            log.error("解析刷新token响应失败: {}", ex.getMessage());
        }

        return false;
    }

    /**
     * 获取当前有效的 accessToken，必要时自动刷新
     */
    private String getValidAccessToken() {
        tokenLock.readLock().lock();
        try {
            // token 未过期，直接返回
            if (accessToken != null && tokenExpireTime != null && Instant.now().isBefore(tokenExpireTime)) {
                return accessToken;
            }
        } finally {
            tokenLock.readLock().unlock();
        }

        // token 已过期，尝试刷新
        tokenLock.writeLock().lock();
        try {
            // 双重检查，避免重复刷新
            if (accessToken != null && tokenExpireTime != null && Instant.now().isBefore(tokenExpireTime)) {
                return accessToken;
            }

            if (refreshToken != null && !refreshToken.isBlank()) {
                log.info("accessToken已过期，尝试刷新...");
                if (refreshAccessToken()) {
                    return accessToken;
                }
            }

            log.warn("accessToken不可用，请重新授权");
            return accessToken; // 返回可能过期的 token，让请求自行失败

        } finally {
            tokenLock.writeLock().unlock();
        }
    }

    /**
     * 更新 token 并持久化
     */
    private void updateToken(String newAccessToken, String newRefreshToken, long expireInSeconds) {
        tokenLock.writeLock().lock();
        try {
            this.accessToken = newAccessToken;
            if (newRefreshToken != null) {
                this.refreshToken = newRefreshToken;
            }
            // 提前5分钟视为过期，留出刷新缓冲
            this.tokenExpireTime = Instant.now().plusSeconds(expireInSeconds - 300);
        } finally {
            tokenLock.writeLock().unlock();
        }
        saveTokenToFile();
    }

    /**
     * 清除 token
     */
    private void clearToken() {
        tokenLock.writeLock().lock();
        try {
            this.accessToken = null;
            this.refreshToken = null;
            this.tokenExpireTime = null;
        } finally {
            tokenLock.writeLock().unlock();
        }
        saveTokenToFile();
    }

    /**
     * 持久化 token 到文件
     */
    private void saveTokenToFile() {
        tokenLock.readLock().lock();
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", accessToken);
            data.put("refreshToken", refreshToken);
            data.put("expireTime", tokenExpireTime != null ? tokenExpireTime.toString() : null);

            Files.createDirectories(tokenStorePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tokenStorePath.toFile(), data);
            log.debug("Token已保存到文件: {}", tokenStorePath);

        } catch (IOException ex) {
            log.warn("保存token到文件失败: {}", ex.getMessage());
        } finally {
            tokenLock.readLock().unlock();
        }
    }

    /**
     * 从文件恢复 token
     */
    private void loadTokenFromFile() {
        if (!Files.exists(tokenStorePath)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(tokenStorePath.toFile());
            String savedAccessToken = root.path("accessToken").asText(null);
            String savedRefreshToken = root.path("refreshToken").asText(null);
            String savedExpireTime = root.path("expireTime").asText(null);

            tokenLock.writeLock().lock();
            try {
                // 只在配置未提供时使用文件中的值
                if (this.accessToken == null || this.accessToken.isBlank()) {
                    this.accessToken = savedAccessToken;
                }
                if (this.refreshToken == null || this.refreshToken.isBlank()) {
                    this.refreshToken = savedRefreshToken;
                }
                if (savedExpireTime != null) {
                    this.tokenExpireTime = Instant.parse(savedExpireTime);
                }
            } finally {
                tokenLock.writeLock().unlock();
            }

            log.info("从文件恢复token成功");

        } catch (Exception ex) {
            log.warn("从文件恢复token失败: {}", ex.getMessage());
        }
    }

    /**
     * 获取扫码登录的二维码
     *
     * @return 二维码信息（qrCodeUrl, uniKey），失败返回null
     */
    public QrCodeInfo getQrCode() {
        String bizContent = "{\"type\":2,\"expiredKey\":\"300\"}";
        String response = doTokenRequest("/openapi/music/basic/user/oauth2/qrcodekey/get/v2", bizContent);

        if (response == null) return null;

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                log.error("获取二维码失败: code={}, message={}", code, root.path("message").asText());
                return null;
            }

            JsonNode data = root.path("data");
            String qrCodeUrl = data.path("qrCodeUrl").asText(null);
            String uniKey = data.path("uniKey").asText(null);

            if (qrCodeUrl != null && uniKey != null) {
                return new QrCodeInfo(qrCodeUrl, uniKey);
            }

        } catch (Exception ex) {
            log.error("解析二维码响应失败: {}", ex.getMessage());
        }

        return null;
    }

    /**
     * 轮询二维码扫码状态
     *
     * @param uniKey 二维码key
     * @return 扫码结果
     */
    public QrCodePollResult pollQrCodeStatus(String uniKey) {
        String bizContent = String.format("{\"key\":\"%s\",\"clientId\":\"%s\"}", uniKey, appId);
        String response = doTokenRequest("/openapi/music/basic/oauth2/device/login/qrcode/get", bizContent);

        if (response == null) return new QrCodePollResult(804, "请求失败", null, null, 0);

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                return new QrCodePollResult(804, root.path("message").asText("未知错误"), null, null, 0);
            }

            JsonNode data = root.path("data");
            int status = data.path("status").asInt(804);
            String msg = data.path("msg").asText("");

            // 扫码成功，提取token
            if (status == 803) {
                JsonNode tokenData = data.path("accessToken");
                String newAccessToken = tokenData.path("accessToken").asText(null);
                String newRefreshToken = tokenData.path("refreshToken").asText(null);
                long expireTime = tokenData.path("expireTime").asLong(604800);

                if (newAccessToken != null && !"null".equals(newAccessToken)) {
                    updateToken(newAccessToken, newRefreshToken, expireTime);
                }

                return new QrCodePollResult(status, msg, newAccessToken, newRefreshToken, expireTime);
            }

            return new QrCodePollResult(status, msg, null, null, 0);

        } catch (Exception ex) {
            log.error("解析二维码轮询响应失败: {}", ex.getMessage());
            return new QrCodePollResult(804, "解析响应失败", null, null, 0);
        }
    }

    /**
     * 二维码信息
     */
    public record QrCodeInfo(String qrCodeUrl, String uniKey) {}

    /**
     * 二维码轮询结果
     * status: 800=过期, 801=等待扫码, 802=授权中, 803=成功, 804=错误
     */
    public record QrCodePollResult(int status, String msg, String accessToken, String refreshToken, long expireTime) {
        public boolean isSuccess() { return status == 803; }
        public boolean isWaiting() { return status == 801 || status == 802; }
        public boolean isExpired() { return status == 800; }
    }

    /**
     * 搜索歌曲
     *
     * @param keyword 搜索关键词
     * @param limit   返回数量
     * @return 歌曲列表
     */
    public List<SongInfo> searchSong(String keyword, int limit) {
        String bizContent = String.format("{\"keyword\":\"%s\",\"limit\":\"%d\",\"offset\":\"0\"}",
                escapeJson(keyword), limit);

        String response = doRequest("/openapi/music/basic/search/song/get/v2", bizContent);
        return parseSongList(response);
    }

    /**
     * 获取歌曲播放URL
     *
     * @param songId  歌曲ID
     * @param bitrate 码率：128/192/320/999/1999
     * @return 播放URL，失败返回null
     */
    public PlayUrlResult getPlayUrl(String songId, int bitrate) {
        String bizContent = String.format("{\"songId\":\"%s\",\"bitrate\":%d}", songId, bitrate);

        String response = doRequest("/openapi/music/basic/song/playurl/get/v2", bizContent);
        return parsePlayUrl(response);
    }

    /**
     * 执行API请求（POST 表单提交，对齐官方 demo）
     */
    private String doRequest(String path, String bizContent) {
        try {
            String currentToken = getValidAccessToken();
            long timestamp = System.currentTimeMillis();
            String device = buildDeviceJson();

            // 构建签名参数（包含 appSecret 用于签名计算，使用原始值不编码）
            Map<String, String> params = new LinkedHashMap<>();
            params.put("appId", appId);
            params.put("appSecret", appSecret);
            params.put("signType", "RSA_SHA256");
            params.put("timestamp", String.valueOf(timestamp));
            params.put("device", device);
            params.put("bizContent", bizContent);
            params.put("accessToken", currentToken != null ? currentToken : "");

            // 计算签名
            String sign = signSha256(params);
            if (sign.isEmpty()) {
                log.error("API请求签名计算失败: path={}", path);
                return null;
            }

            // appSecret 仅用于签名，不应出现在请求体中
            params.remove("appSecret");
            params.put("sign", sign);

            // 对齐官方 demo：device、bizContent、sign 单独编码后替换原值
            params.put("device", encodeURIComponent(device));
            params.put("bizContent", encodeURIComponent(bizContent));
            params.put("sign", encodeURIComponent(sign));

            // 构建表单提交体
            StringBuilder formBody = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) formBody.append("&");
                formBody.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }

            String url = baseUrl + path;
            log.debug("网易云音乐API请求: {}", path);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResp.body();

            log.debug("网易云音乐API响应: HTTP {}", httpResp.statusCode());

            if (httpResp.statusCode() / 100 != 2) {
                log.error("网易云音乐API请求失败: HTTP {}, body={}", httpResp.statusCode(), body);
                return null;
            }

            return body;

        } catch (Exception ex) {
            log.error("网易云音乐API请求异常: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 执行 Token 相关请求（POST 表单提交，对齐官方 demo）
     * 注意：即使获取二维码也需要传 accessToken 参数（可传空或占位符）
     */
    private String doTokenRequest(String path, String bizContent) {
        try {
            long timestamp = System.currentTimeMillis();
            String device = buildDeviceJson();

            // 构建签名参数（包含 appSecret 用于签名计算，使用原始值不编码）
            Map<String, String> params = new LinkedHashMap<>();
            params.put("appId", appId);
            params.put("appSecret", appSecret);
            params.put("signType", "RSA_SHA256");
            params.put("timestamp", String.valueOf(timestamp));
            params.put("device", device);
            params.put("bizContent", bizContent);
            params.put("accessToken", accessToken != null ? accessToken : "");

            // 计算签名（signSha256 内部按 key 排序拼接，符合官方规范）
            String sign = signSha256(params);
            if (sign.isEmpty()) {
                log.error("签名计算失败，无法发送请求");
                return null;
            }

            log.info("签名结果: {}", sign);

            // appSecret 仅用于签名，不应出现在请求体中
            params.remove("appSecret");
            params.put("sign", sign);

            // 对齐官方 demo：device、bizContent、sign 单独编码后替换原值
            params.put("device", encodeURIComponent(device));
            params.put("bizContent", encodeURIComponent(bizContent));
            params.put("sign", encodeURIComponent(sign));

            // 构建表单提交体（对齐官方 demo 的 getSignCheckContent 格式）
            StringBuilder formBody = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) formBody.append("&");
                formBody.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }

            String url = baseUrl + path;
            log.info("请求URL: {}, 表单参数: {}", url, formBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                    .build();

            HttpResponse<String> httpResp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = httpResp.body();

            log.info("网易云音乐Token响应: HTTP {}, body={}", httpResp.statusCode(), body);

            if (httpResp.statusCode() / 100 != 2) {
                log.error("网易云音乐Token请求失败: HTTP {}, body={}", httpResp.statusCode(), body);
                return null;
            }

            return body;

        } catch (Exception ex) {
            log.error("网易云音乐Token请求异常: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * JavaScript encodeURIComponent 兼容的URL编码
     */
    private String encodeURIComponent(String str) {
        if (str == null) return "";
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return str;
        }
    }

    /**
     * 计算 RSA SHA256 签名（对齐官方 demo 的 getSignCheckContent + rsa256Sign）
     * 规则：移除 sign 参数，按 key 字母排序，拼接 key=value，RSA SHA256 签名后 Base64
     */
    private String signSha256(Map<String, String> params) {
        try {
            if (privateKey == null || privateKey.isBlank()) {
                log.error("RSA私钥未配置，请在配置文件中设置 wxclaw.music.private-key");
                return "";
            }

            // 对齐官方 demo：先移除 sign，再排序拼接
            Map<String, String> paramsWithoutSign = new LinkedHashMap<>(params);
            paramsWithoutSign.remove("sign");

            List<String> keys = new ArrayList<>(paramsWithoutSign.keySet());
            Collections.sort(keys);

            StringBuilder content = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                String value = paramsWithoutSign.get(key);
                if (i > 0) content.append("&");
                content.append(key).append("=").append(value);
            }

            String signString = content.toString();
            log.info("签名字符串: {}", signString);

            PrivateKey priKey = getPrivateKeyFromPKCS8(privateKey);
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(priKey);
            signature.update(signString.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();

            return Base64.getEncoder().encodeToString(signed);

        } catch (Exception ex) {
            log.error("签名计算失败: {}", ex.getMessage(), ex);
            return "";
        }
    }
    
    /**
     * 从PKCS8格式的私钥字符串获取PrivateKey对象
     */
    private PrivateKey getPrivateKeyFromPKCS8(String privateKeyStr) throws Exception {
        // 移除可能的头尾标记和换行符
        String key = privateKeyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        
        byte[] encodedKey = Base64.getDecoder().decode(key);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encodedKey));
    }

    /**
     * 构建设备信息JSON
     * 使用 openapi 类型（与官方demo一致）
     */
    private String buildDeviceJson() {
        return String.format(
                "{\"deviceType\":\"%s\",\"os\":\"%s\",\"appVer\":\"1.0.0\",\"channel\":\"%s\",\"model\":\"%s\",\"deviceId\":\"%s\",\"brand\":\"%s\",\"osVer\":\"1.0.0\",\"clientIp\":\"192.168.0.1\"}",
                escapeJson(deviceType), escapeJson(deviceOs), escapeJson(deviceChannel),
                escapeJson(deviceType), escapeJson(deviceId), escapeJson(deviceBrand));
    }

    /**
     * 解析歌曲列表
     */
    private List<SongInfo> parseSongList(String response) {
        List<SongInfo> result = new ArrayList<>();
        if (response == null) return result;

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                log.warn("网易云音乐搜索失败: code={}, message={}", code, root.path("message").asText());
                return result;
            }

            JsonNode records = root.path("data").path("records");
            if (records == null || !records.isArray()) return result;

            for (JsonNode record : records) {
                String id = record.path("id").asText(null);
                String name = record.path("name").asText(null);
                boolean playFlag = record.path("playFlag").asBoolean(false);
                boolean visible = record.path("visible").asBoolean(true);
                boolean vipFlag = record.path("vipFlag").asBoolean(false);
                String coverImgUrl = record.path("coverImgUrl").asText(null);
                long duration = record.path("duration").asLong(0);

                // 提取艺人名
                List<String> artistNames = new ArrayList<>();
                JsonNode artists = record.path("artists");
                if (artists.isArray()) {
                    for (JsonNode artist : artists) {
                        artistNames.add(artist.path("name").asText(""));
                    }
                }
                if (artistNames.isEmpty()) {
                    JsonNode fullArtists = record.path("fullArtists");
                    if (fullArtists != null && fullArtists.isArray()) {
                        for (JsonNode artist : fullArtists) {
                            artistNames.add(artist.path("name").asText(""));
                        }
                    }
                }

                // 提取专辑名
                String albumName = record.path("album").path("name").asText(null);

                if (id != null && name != null) {
                    result.add(new SongInfo(id, name, artistNames, albumName,
                            coverImgUrl, duration, playFlag, visible, vipFlag));
                }
            }

        } catch (Exception ex) {
            log.error("解析网易云音乐搜索结果失败: {}", ex.getMessage());
        }

        return result;
    }

    /**
     * 解析播放URL
     */
    private PlayUrlResult parsePlayUrl(String response) {
        if (response == null) return new PlayUrlResult(null, 0, "请求失败");

        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                return new PlayUrlResult(null, 0, "API返回错误: " + code);
            }

            String subCode = root.path("subCode").asText("200");
            JsonNode data = root.path("data");
            String url = data.path("url").asText(null);
            int br = data.path("br").asInt(0);

            if ("200".equals(subCode) && url != null) {
                return new PlayUrlResult(url, br, null);
            }

            // 处理错误码
            String message = switch (subCode) {
                case "10003" -> "因版权要求，请前往网易云音乐APP收听";
                case "10004" -> "该歌曲为付费歌曲，请前往网易云音乐APP购买后收听";
                default -> root.path("message").asText("获取播放地址失败");
            };

            return new PlayUrlResult(null, 0, message);

        } catch (Exception ex) {
            log.error("解析网易云音乐播放URL失败: {}", ex.getMessage());
            return new PlayUrlResult(null, 0, "解析响应失败");
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 歌曲信息
     */
    public record SongInfo(
            String id,
            String name,
            List<String> artists,
            String albumName,
            String coverImgUrl,
            long duration,
            boolean playFlag,
            boolean visible,
            boolean vipFlag
    ) {
        public String getArtistString() {
            return artists != null ? String.join("/", artists) : "未知";
        }

        public String getDurationString() {
            if (duration <= 0) return "";
            long seconds = duration / 1000;
            return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }
    }

    /**
     * 播放URL结果
     */
    public record PlayUrlResult(String url, int bitrate, String error) {
        public boolean isSuccess() {
            return url != null && !url.isBlank();
        }
    }
}
