package com.dust.wxclawbackfront.bot.knowledge;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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

    private static Path createTemp() {
        try { return Files.createTempDirectory("knowledge-store-test"); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
