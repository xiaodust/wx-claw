package com.dust.wxclawbackfront.ilnk.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TempMediaStore {

    private final Path baseDir;
    private final Duration ttl;
    private final int maxEntries;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public TempMediaStore(@Value("${wxclaw.media.temp.dir:}") String baseDir,
                          @Value("${wxclaw.media.temp.ttl:PT10M}") Duration ttl,
                          @Value("${wxclaw.media.temp.max-entries:200}") int maxEntries) {
        this.baseDir = initBaseDir(baseDir);
        this.ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
        this.maxEntries = Math.max(50, maxEntries);
    }

    public MediaRef put(byte[] bytes, String contentType, String ext) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes is empty");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        String safeExt = (ext == null || ext.isBlank()) ? "bin" : ext.trim();
        Path path = baseDir.resolve(id + "." + safeExt);
        try {
            Files.createDirectories(baseDir);
            Files.write(path, bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("write temp file failed: " + ex.getMessage(), ex);
        }

        Entry entry = new Entry(path, contentType, Instant.now().plus(ttl));
        entries.put(id, entry);
        evictIfNeeded();
        return new MediaRef(id, "/api/media/" + id, contentType, path.toAbsolutePath().toString());
    }

    public Entry get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Entry entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(id);
            tryDelete(entry.path());
            return null;
        }
        return entry;
    }

    private void evictIfNeeded() {
        if (entries.size() <= maxEntries) {
            return;
        }
        entries.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().expiresAt()))
                .limit(Math.max(1, entries.size() - maxEntries))
                .toList()
                .forEach(e -> {
                    entries.remove(e.getKey());
                    tryDelete(e.getValue().path());
                });
    }

    private static void tryDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static Path initBaseDir(String baseDir) {
        if (baseDir != null && !baseDir.isBlank()) {
            return Path.of(baseDir.trim());
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "wxclaw-media");
    }

    public record MediaRef(String id, String relativeUrl, String contentType, String localPath) {
    }

    public record Entry(Path path, String contentType, Instant expiresAt) {
    }
}
