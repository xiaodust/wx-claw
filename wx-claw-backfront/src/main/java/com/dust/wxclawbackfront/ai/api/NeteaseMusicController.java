package com.dust.wxclawbackfront.ai.api;

import com.dust.wxclawbackfront.ai.tools.music.NeteaseMusicClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

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

    /**
     * 获取扫码登录二维码
     * 返回二维码URL，用户用网易云APP扫码后调用 /api/music/poll 轮询状态
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
                "message", "请用网易云音乐APP扫描二维码登录"
        ));
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
