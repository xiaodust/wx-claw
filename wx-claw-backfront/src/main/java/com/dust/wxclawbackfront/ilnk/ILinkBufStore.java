package com.dust.wxclawbackfront.ilnk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ILinkBufStore {

    private ILinkBufStore() {
    }

    public static Optional<String> env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public static Optional<String> readFile(String filePath) {
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

    public static void writeFile(String filePath, String content) {
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
