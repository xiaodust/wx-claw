package com.dust.wxclawbackfront.bot.agent.career.context;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient;
import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.JobHelperMcpClient.ResumeFile;
import com.dust.wxclawbackfront.bot.agent.career.config.JobHelperProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CareerResumeContextStore {
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};

    private final JobHelperProperties properties;
    private final JobHelperMcpClient client;
    private final ConcurrentMap<CareerUserKey, Entry> resumes = new ConcurrentHashMap<>();

    public CareerResumeContextStore(JobHelperProperties properties, JobHelperMcpClient client) {
        this.properties = properties;
        this.client = client;
    }

    public StoreResult storeCurrent(String fileName, byte[] fileBytes) {
        if (!properties.isEnabled()) return StoreResult.featureDisabled();
        if (!isPdf(fileName, fileBytes)) {
            return StoreResult.rejection("职业服务目前只支持 PDF 简历");
        }
        if (fileBytes.length > properties.getMaxResumeSize().toBytes()) {
            return StoreResult.rejection("PDF 简历超过 10MB 限制");
        }
        PendingResume resume = new PendingResume(normalizeFileName(fileName), fileBytes, sha256(fileBytes));
        CareerUserKey key = CareerUserKey.current();
        client.saveResume(key.jobHelperIdentity(), new ResumeFile(
                resume.fileName(), resume.fileBytes(), resume.sha256()));
        resumes.put(key, new Entry(resume, expiresAt()));
        return StoreResult.success();
    }

    public Optional<PendingResume> getCurrent() {
        CareerUserKey key = CareerUserKey.current();
        Entry entry = resumes.get(key);
        if (entry == null) {
            entry = loadRemote(key).map(resume -> new Entry(resume, expiresAt())).orElse(null);
            if (entry != null) resumes.put(key, entry);
        }
        if (entry == null) return Optional.empty();
        if (!entry.expiresAt().isAfter(Instant.now())) {
            resumes.remove(key, entry);
            return loadRemote(key).map(resume -> {
                resumes.put(key, new Entry(resume, expiresAt()));
                return resume;
            });
        }
        return Optional.of(entry.resume());
    }

    public boolean clearCurrent() {
        CareerUserKey key = CareerUserKey.current();
        Entry removed = resumes.remove(key);
        return client.deleteResume(key.jobHelperIdentity()) || removed != null;
    }

    @Scheduled(fixedDelayString = "${wxclaw.career.resume-context-cleanup-ms:60000}")
    void removeExpired() {
        Instant now = Instant.now();
        resumes.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    int size() {
        return resumes.size();
    }

    private Instant expiresAt() {
        return Instant.now().plus(properties.getResumeContextTtl());
    }

    private Optional<PendingResume> loadRemote(CareerUserKey key) {
        var current = client.currentResume(key.jobHelperIdentity());
        if (!current.exists() || current.resume() == null) return Optional.empty();
        byte[] bytes = client.readResume(current.resume().resourceUri());
        return Optional.of(new PendingResume(current.resume().fileName(), bytes, current.resume().sha256()));
    }

    private boolean isPdf(String fileName, byte[] bytes) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf") || bytes == null
                || bytes.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (bytes[index] != PDF_SIGNATURE[index]) return false;
        }
        return true;
    }

    private String normalizeFileName(String fileName) {
        return fileName == null || fileName.isBlank() ? "resume.pdf" : fileName;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Entry(PendingResume resume, Instant expiresAt) {
    }

    public record PendingResume(String fileName, byte[] fileBytes, String sha256) {
        public PendingResume {
            fileBytes = fileBytes.clone();
        }

        @Override
        public byte[] fileBytes() {
            return fileBytes.clone();
        }
    }

    public record StoreResult(boolean stored, boolean enabled, String message) {
        static StoreResult success() {
            return new StoreResult(true, true, null);
        }

        static StoreResult rejection(String message) {
            return new StoreResult(false, true, message);
        }

        static StoreResult featureDisabled() {
            return new StoreResult(false, false, null);
        }
    }
}
