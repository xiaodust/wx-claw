package com.dust.wxclawbackfront.bot.knowledge;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/** Durable raw-file store used when a knowledge-base file must be returned verbatim. */
@Component
public class KnowledgeFileStore {
    private final Path root;

    public KnowledgeFileStore(@Value("${wxclaw.knowledge.file-storage-dir:.uploads/knowledge-files}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredFile store(String fileName, byte[] bytes, String label) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("文件内容不能为空");
        String safeName = safeName(fileName);
        String safeLabel = normalizeLabel(label);
        String key = digest(identity() + "|" + safeLabel);
        try {
            Files.createDirectories(root);
            Path data = root.resolve(key + ".bin");
            Path metadata = root.resolve(key + ".meta");
            Files.write(data, bytes);
            Files.writeString(metadata, safeLabel + "\n" + safeName + "\n" + digest(bytes) + "\n" + Instant.now(), StandardCharsets.UTF_8);
            return new StoredFile(safeLabel, safeName, bytes.clone(), digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("无法持久化知识库文件", ex);
        }
    }

    public Optional<StoredFile> find(String label) {
        String safeLabel = normalizeLabel(label);
        String key = digest(identity() + "|" + safeLabel);
        Path data = root.resolve(key + ".bin");
        Path metadata = root.resolve(key + ".meta");
        try {
            if (!Files.exists(data) || !Files.exists(metadata)) return Optional.empty();
            String[] lines = Files.readString(metadata, StandardCharsets.UTF_8).split("\\R", -1);
            if (lines.length < 3 || !safeLabel.equals(lines[0])) return Optional.empty();
            byte[] bytes = Files.readAllBytes(data);
            String hash = digest(bytes);
            if (!hash.equals(lines[2])) return Optional.empty();
            return Optional.of(new StoredFile(lines[0], lines[1], bytes, hash));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String identity() {
        TenantContext context = TenantContextHolder.getNullable();
        if (context == null) return "default|default|default";
        return String.join("|", safe(context.tenantId()), safe(context.botId()), safe(context.internalUserId()));
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "uploaded-file";
        return Path.of(name).getFileName().toString().replaceAll("[\\r\\n\\\\/]", "_");
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) return "file";
        return label.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}._-]", "-");
    }

    private String safe(String value) { return value == null ? "unknown" : value; }

    private String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private String digest(String value) { return digest(value.getBytes(StandardCharsets.UTF_8)); }

    public record StoredFile(String label, String fileName, byte[] bytes, String sha256) {
        public StoredFile { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
