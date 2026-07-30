package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaContextManagerTest {

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
