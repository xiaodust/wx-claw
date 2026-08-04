package com.dust.wxclawbackfront.bot.knowledge;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Durable raw-file store used when a knowledge-base file must be returned verbatim. */
@Slf4j
@Component
public class KnowledgeFileStore {
    private final Path root;

    @Value("${wxclaw.knowledge.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${wxclaw.knowledge.file-retention-days:90}")
    private int fileRetentionDays;

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

    /**
     * 删除指定标签对应的原始文件与元数据。
     */
    public void delete(String label) {
        String safeLabel = normalizeLabel(label);
        String key = digest(identity() + "|" + safeLabel);
        try {
            boolean deleted = Files.deleteIfExists(root.resolve(key + ".bin"));
            deleted |= Files.deleteIfExists(root.resolve(key + ".meta"));
            if (deleted) {
                log.info("已删除知识库原始文件: label={}", safeLabel);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("无法删除知识库文件", ex);
        }
    }

    /**
     * 定时删除超过保留期（按文件修改时间）的原始文件，防止磁盘无限增长。
     */
    @Scheduled(cron = "${wxclaw.knowledge.cleanup-cron:0 15 4 * * ?}")
    public void cleanupExpiredScheduled() {
        if (!cleanupEnabled) {
            return;
        }
        long deleted = cleanupOlderThan(Duration.ofDays(Math.max(1, fileRetentionDays)));
        if (deleted > 0) {
            log.info("已清理 {} 个超过保留期的知识库文件（保留 {} 天）", deleted, fileRetentionDays);
        }
    }

    /**
     * 删除修改时间早于 {@code maxAge} 的 {@code .bin}/{@code .meta} 文件。
     */
    public long cleanupOlderThan(Duration maxAge) {
        if (maxAge == null || maxAge.isNegative() || !Files.isDirectory(root)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        long deleted = 0;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".bin") && !name.endsWith(".meta")) {
                    continue;
                }
                if (path.toFile().lastModified() > cutoff.toEpochMilli()) {
                    continue;
                }
                try {
                    if (Files.deleteIfExists(path)) {
                        deleted++;
                    }
                } catch (IOException ex) {
                    log.warn("清理知识库文件失败: {}", path, ex);
                }
            }
        } catch (IOException ex) {
            log.warn("扫描知识库目录失败: {}", root, ex);
        }
        return deleted;
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
