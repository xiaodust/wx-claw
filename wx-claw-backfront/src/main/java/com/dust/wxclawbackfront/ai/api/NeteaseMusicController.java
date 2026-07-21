package com.dust.wxclawbackfront.ai.api;

import com.dust.wxclawbackfront.ai.tools.music.NeteaseMusicClient;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云音乐授权接口
 */
@Slf4j
@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
@ConditionalOnBean(NeteaseMusicClient.class)
public class NeteaseMusicController {

    private final NeteaseMusicClient musicClient;

    @Value("${wxclaw.music.app-id:}")
    private String appId;

    @Value("${wxclaw.music.app-secret:}")
    private String appSecret;

    @Value("${wxclaw.music.private-key:}")
    private String privateKey;

    /**
     * 获取扫码登录二维码（返回JSON，包含二维码图片地址）
     * 用户用网易云APP扫码后调用 /api/music/poll 轮询状态
     */
    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> getQrCode() {
        NeteaseMusicClient.QrCodeInfo qrCode = musicClient.getQrCode();

        if (qrCode == null) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "获取二维码失败"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "qrCodeUrl", qrCode.qrCodeUrl(),
                "uniKey", qrCode.uniKey(),
                "imageApi", "/api/music/qrcode/image?content=" + qrCode.qrCodeUrl(),
                "message", "请用网易云音乐APP扫描二维码登录（访问 imageApi 获取二维码图片）"
        ));
    }

    /**
     * 生成二维码图片（PNG）
     * 浏览器直接访问此地址即可看到二维码图片
     * @param content 二维码内容（URL）
     */
    @GetMapping(value = "/qrcode/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCodeImage(@RequestParam String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return ResponseEntity.ok(baos.toByteArray());
        } catch (Exception e) {
            log.error("生成二维码图片失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 轮询扫码状态
     * @param uniKey 二维码key（从 /qrcode 接口获取）
     */
    @GetMapping("/poll")
    public ResponseEntity<Map<String, Object>> pollStatus(@RequestParam String uniKey) {
        NeteaseMusicClient.QrCodePollResult result = musicClient.pollQrCodeStatus(uniKey);

        Map<String, Object> response = new HashMap<>();
        response.put("status", result.status());
        response.put("message", result.msg());

        if (result.isSuccess()) {
            response.put("success", true);
            response.put("message", "扫码登录成功！token已保存。");
        } else if (result.isExpired()) {
            response.put("success", false);
            response.put("message", "二维码已过期，请重新获取");
        } else if (result.isWaiting()) {
            response.put("success", false);
            response.put("message", "等待扫码中...");
        } else {
            response.put("success", false);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 通过授权码换取token
     * @param code 授权码
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> exchangeToken(@RequestParam String code) {
        boolean success = musicClient.exchangeToken(code);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "token获取成功"
            ));
        }

        return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "token获取失败"
        ));
    }

    /**
     * 测试API连接（返回原始响应，方便排查问题）
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testApi() {
        Map<String, Object> result = new HashMap<>();
        result.put("appId", appId != null ? appId.substring(0, Math.min(8, appId.length())) + "..." : "null");
        result.put("appSecret", appSecret != null ? appSecret.substring(0, Math.min(8, appSecret.length())) + "..." : "null");
        result.put("privateKeyLength", privateKey != null ? privateKey.length() : 0);

        try {
            NeteaseMusicClient.QrCodeInfo qrCode = musicClient.getQrCode();
            if (qrCode != null) {
                result.put("success", true);
                result.put("qrCodeUrl", qrCode.qrCodeUrl());
                result.put("uniKey", qrCode.uniKey());
            } else {
                result.put("success", false);
                result.put("message", "getQrCode() 返回 null，请查看后台日志中的详细错误信息");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 扫码登录页面（浏览器直接打开即可扫码）
     */
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> loginPage() {
        // 配置诊断
        List<String> issues = new ArrayList<>();
        if (appId == null || appId.isBlank() || appId.equals("your-app-id")) {
            issues.add("wxclaw.music.app-id 未配置或为占位符");
        }
        if (appSecret == null || appSecret.isBlank() || appSecret.equals("your-app-secret")) {
            issues.add("wxclaw.music.app-secret 未配置或为占位符");
        }
        if (privateKey == null || privateKey.isBlank() || privateKey.equals("your-private-key")) {
            issues.add("wxclaw.music.private-key 未配置或为占位符");
        }

        if (!issues.isEmpty()) {
            return ResponseEntity.internalServerError().body(buildErrorPage(issues));
        }

        NeteaseMusicClient.QrCodeInfo qrCode = musicClient.getQrCode();

        if (qrCode == null) {
            return ResponseEntity.internalServerError().body(buildErrorPage(List.of(
                    "调用网易云API获取二维码失败",
                    "请先访问 <a href='/api/music/test'>/api/music/test</a> 查看详细错误信息",
                    "然后查看后台日志中的完整API响应"
            )));
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>网易云音乐扫码登录</title>
                    <style>
                        body { font-family: -apple-system, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #f5f5f5; }
                        .card { background: #fff; border-radius: 12px; padding: 40px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); text-align: center; max-width: 400px; }
                        h2 { color: #333; margin-bottom: 8px; }
                        .tip { color: #888; font-size: 14px; margin-bottom: 24px; }
                        img { border: 1px solid #eee; border-radius: 8px; }
                        #status { margin-top: 20px; font-size: 16px; color: #666; }
                        .success { color: #52c41a; font-weight: bold; }
                        .expired { color: #ff4d4f; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>网易云音乐扫码登录</h2>
                        <p class="tip">打开网易云音乐APP &rarr; 扫一扫 &rarr; 扫描下方二维码</p>
                        <img id="qrcode" src="/api/music/qrcode/image?content=%s" width="300" height="300" alt="二维码">
                        <div id="status">等待扫码...</div>
                    </div>
                    <script>
                        const uniKey = '%s';
                        let polling = true;
                        async function poll() {
                            while (polling) {
                                try {
                                    const resp = await fetch('/api/music/poll?uniKey=' + uniKey);
                                    const data = await resp.json();
                                    const el = document.getElementById('status');
                                    if (data.success) {
                                        el.className = 'success';
                                        el.textContent = '\u2713 ' + data.message;
                                        polling = false;
                                        return;
                                    }
                                    if (data.status === 803) {
                                        el.className = 'expired';
                                        el.textContent = '二维码已过期，请刷新页面重新获取';
                                        polling = false;
                                        return;
                                    }
                                    el.textContent = '等待扫码...';
                                } catch (e) {
                                    console.error(e);
                                }
                                await new Promise(r => setTimeout(r, 2000));
                            }
                        }
                        poll();
                    </script>
                </body>
                </html>
                """.formatted(
                qrCode.qrCodeUrl().replace("&", "&amp;"),
                qrCode.uniKey()
        );

        return ResponseEntity.ok(html);
    }

    private String buildErrorPage(List<String> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>网易云音乐登录 - 配置错误</title>
                    <style>
                        body { font-family: -apple-system, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background: #f5f5f5; }
                        .card { background: #fff; border-radius: 12px; padding: 40px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); max-width: 500px; }
                        h2 { color: #ff4d4f; margin-bottom: 16px; }
                        ul { color: #666; line-height: 2; }
                        code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
                        .hint { margin-top: 20px; padding: 16px; background: #fff7e6; border-radius: 8px; color: #ad6800; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>配置不完整</h2>
                        <ul>
                """);
        for (String issue : issues) {
            sb.append("            <li>").append(issue).append("</li>\n");
        }
        sb.append("""
                        </ul>
                        <div class="hint">
                            请在 <code>application.yml</code> 中配置真实的网易云开放平台凭证：<br><br>
                            <code>wxclaw.music.app-id</code><br>
                            <code>wxclaw.music.app-secret</code><br>
                            <code>wxclaw.music.private-key</code><br><br>
                            获取地址：<a href="https://open.music.163.com" target="_blank">open.music.163.com</a>
                        </div>
                    </div>
                </body>
                </html>
                """);
        return sb.toString();
    }

    /**
     * 刷新token
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken() {
        boolean success = musicClient.refreshAccessToken();

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "token刷新成功"
            ));
        }

        return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "token刷新失败，请重新扫码登录"
        ));
    }
}
