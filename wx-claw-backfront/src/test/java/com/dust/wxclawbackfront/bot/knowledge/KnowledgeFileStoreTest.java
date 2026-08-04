package com.dust.wxclawbackfront.bot.knowledge;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeFileStoreTest {
    private final Path temp = createTemp();

    @AfterEach
    void cleanup() throws Exception { TenantContextHolder.clear(); Files.walk(temp).sorted((a,b) -> b.compareTo(a)).forEach(p -> p.toFile().delete()); }

    @Test
    void storesAndRetrievesExactBytesByUserAndLabel() {
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
        KnowledgeFileStore store = new KnowledgeFileStore(temp.toString());
        byte[] original = {0, 1, 2, 3, (byte) 255};
        store.store("my resume.pdf", original, "resume");
        original[0] = 99;
        assertArrayEquals(new byte[]{0, 1, 2, 3, (byte) 255}, store.find("resume").orElseThrow().bytes());
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "other-user", "req"));
        assertTrue(store.find("resume").isEmpty());
    }

    @Test
    void deletesStoredFileByLabel() throws Exception {
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
        KnowledgeFileStore store = new KnowledgeFileStore(temp.toString());
        store.store("resume.pdf", new byte[]{1, 2, 3}, "resume");

        assertTrue(store.find("resume").isPresent());
        store.delete("resume");

        assertTrue(store.find("resume").isEmpty());
        try (Stream<Path> paths = Files.list(temp)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void cleanupOlderThanRemovesOnlyExpiredFiles() throws Exception {
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
        KnowledgeFileStore store = new KnowledgeFileStore(temp.toString());
        store.store("old.pdf", new byte[]{1}, "old");
        ageAllFiles(temp, Duration.ofDays(2));
        store.store("new.pdf", new byte[]{2}, "new");

        long deleted = store.cleanupOlderThan(Duration.ofDays(1));

        assertTrue(deleted >= 2, "应删除 old 标签的 .bin/.meta，实际删除 " + deleted);
        assertTrue(store.find("old").isEmpty());
        assertTrue(store.find("new").isPresent());
    }

    private static void ageAllFiles(Path dir, Duration age) throws Exception {
        long oldMillis = Instant.now().minus(age).toEpochMilli();
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.toList()) {
                path.toFile().setLastModified(oldMillis);
            }
        }
    }

    private static Path createTemp() {
        try { return Files.createTempDirectory("knowledge-store-test"); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
