package com.dust.wxclawbackfront.ilnk;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.WeixinMessage;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {

    public static void main(String[] args) {
        String bufFile = env("BUF_FILE").orElse("sync_buf.dat");
        ILinkClient client = ILinkClient.builder().token("").build();

        String initialBuf = readFile(bufFile).orElse(null);

        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String qrCodeUrl) {
                System.out.println("请扫码: " + qrCodeUrl);
            }

            @Override
            public void onScanned() {
                System.out.println("已扫码，请在微信上确认...");
            }

            @Override
            public void onExpired(int attempt, int maxAttempts) {
                System.out.println("二维码已过期，正在刷新 (" + attempt + "/" + maxAttempts + ")");
            }
        });

        if (!result.isConnected()) {
            System.err.println("登录失败: " + result.getMessage());
            return;
        }

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopFlag.set(true)));

        String finalInitialBuf = initialBuf;
        MonitorOptions options = MonitorOptions.builder()
                .initialBuf(finalInitialBuf)
                .onBufUpdate(buf -> writeFile(bufFile, buf))
                .onError(err -> System.err.println("监听错误: " + err.getMessage()))
                .onSessionExpired(() -> System.err.println("会话已过期，请重新登录"))
                .build();

        client.monitor(msg -> onMessage(client, msg), options, stopFlag);
    }

    private static void onMessage(ILinkClient client, WeixinMessage msg) {
        if (msg == null) {
            return;
        }

        String userId = msg.getFromUserId();
        String contextToken = msg.getContextToken();
        if (userId != null && !userId.isBlank() && contextToken != null && !contextToken.isBlank()) {
            client.setContextToken(userId, contextToken);
        }

        String text = MessageHelper.extractText(msg);
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String reply = "已收到: " + trimmed;
        try {
            if (contextToken != null && !contextToken.isBlank()) {
                client.sendText(userId, reply, contextToken);
            } else {
                client.push(userId, reply);
            }
        } catch (Exception ex) {
            System.err.println("发送失败: " + ex.getMessage());
        }
    }

    private static Optional<String> env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private static Optional<String> readFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return Optional.empty();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(content);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static void writeFile(String filePath, String content) {
        if (filePath == null || filePath.isBlank() || content == null) {
            return;
        }
        try {
            Path path = Path.of(filePath);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("保存 BUF 失败: " + ex.getMessage());
        }
    }
}

