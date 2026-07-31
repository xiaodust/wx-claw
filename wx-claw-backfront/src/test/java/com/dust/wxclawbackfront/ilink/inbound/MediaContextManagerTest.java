package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import org.junit.jupiter.api.Test;

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
}
