package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MediaContextManagerTest {

    @Test
    void recognizesNaturalKnowledgeUploadRequests() {
        MediaContextManager manager = new MediaContextManager();

        assertThat(manager.isKnowledgeBaseUploadIntent("帮我存知识库")).isTrue();
        assertThat(manager.isKnowledgeBaseUploadIntent("把这份简历存到数据库里")).isTrue();
        assertThat(manager.isKnowledgeBaseUploadIntent("请导入资料库")).isTrue();
        assertThat(manager.isKnowledgeBaseUploadIntent("不要上传到知识库")).isFalse();
        assertThat(manager.isKnowledgeBaseUploadIntent("帮我分析文件")).isFalse();
    }

    @Test
    void keepsPendingBytesUntilExplicitlyCleared() {
        MediaContextManager manager = new MediaContextManager();
        byte[] content = {1, 2, 3};
        manager.storePendingFileUpload("user-a",
                ILinkUserInput.file(null, "resume.pdf", "3", content, "resume text"));

        assertThat(manager.getPendingFileUpload("user-a").fileBytes()).containsExactly(content);
        assertThat(manager.getPendingFileUpload("user-a").fileBytes()).containsExactly(content);

        manager.clearPendingFileUpload("user-a");
        assertThat(manager.getPendingFileUpload("user-a")).isNull();
    }

    @Test
    void retainsResumeBytesWithoutCreatingPendingPromptContext() {
        MediaContextManager manager = new MediaContextManager();
        byte[] content = {1, 2, 3};

        manager.storePendingFileUpload("user-a",
                ILinkUserInput.file(null, "resume.pdf", "3", content, "resume text"));

        assertThat(manager.takeFileContext("user-a")).isNull();
        MediaContextManager.PendingFileUpload upload = manager.takePendingFileUpload("user-a");
        assertThat(upload.fileName()).isEqualTo("resume.pdf");
        assertThat(upload.fileBytes()).containsExactly(content);
    }

    @Test
    void keepsMediaContextUntilExplicitlyCleared() {
        MediaContextManager manager = new MediaContextManager();
        manager.storeImageContext("user-a", "图片描述");
        manager.storeVideoContext("user-a", "视频描述");

        assertThat(manager.getImageContext("user-a")).isEqualTo("图片描述");
        assertThat(manager.getImageContext("user-a")).isEqualTo("图片描述");
        assertThat(manager.getVideoContext("user-a")).isEqualTo("视频描述");

        manager.clearImageContext("user-a");
        manager.clearVideoContext("user-a");
        assertThat(manager.getImageContext("user-a")).isNull();
        assertThat(manager.getVideoContext("user-a")).isNull();
    }

    @Test
    void cleanupExpiredRemovesStaleContextsAndKeepsFreshOnes() {
        MediaContextManager manager = new MediaContextManager();
        byte[] content = {1, 2, 3};
        Instant base = Instant.now();
        manager.storeImageContext("stale-user", "旧图片");
        manager.storeVideoContext("stale-user", "旧视频");
        manager.storeFileContext("stale-user",
                ILinkUserInput.file(null, "old.pdf", "3", content, "old text"));
        // 模拟 2 小时后触发清理：10 分钟未更新的 stale 用户应被清掉
        manager.cleanupExpired(Duration.ofMinutes(10), base.plus(Duration.ofHours(2)));

        // 清理之后新写入的上下文，即使再过 1 分钟也不应被误删
        manager.storeImageContext("fresh-user", "新图片");
        manager.storeFileContext("fresh-user",
                ILinkUserInput.file(null, "new.pdf", "3", content, "new text"));
        manager.cleanupExpired(Duration.ofMinutes(10), Instant.now());

        assertThat(manager.getImageContext("stale-user")).isNull();
        assertThat(manager.getVideoContext("stale-user")).isNull();
        assertThat(manager.getFileContext("stale-user")).isNull();
        assertThat(manager.getPendingFileUpload("stale-user")).isNull();

        assertThat(manager.getImageContext("fresh-user")).isEqualTo("新图片");
        assertThat(manager.getFileContext("fresh-user")).isNotNull();
        assertThat(manager.getPendingFileUpload("fresh-user")).isNotNull();
    }
}
