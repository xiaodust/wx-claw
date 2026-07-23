package com.dust.wxclawbackfront.bot.agent.tools.shared;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FileUploadValidator {

    @Value("${wxclaw.upload.max-file-size-mb:50}")
    private int maxFileSizeMb;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv", "json", "xml", "html", "htm"
    );

    public ValidationResult validate(String fileName, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return ValidationResult.invalid("文件内容为空");
        }

        long maxSizeBytes = maxFileSizeMb * 1024L * 1024L;
        if (fileBytes.length > maxSizeBytes) {
            return ValidationResult.invalid(
                    String.format("文件大小超过限制（最大 %dMB，当前 %.2fMB）",
                            maxFileSizeMb, fileBytes.length / (1024.0 * 1024.0)));
        }

        if (fileName != null && !fileName.isBlank()) {
            String extension = getFileExtension(fileName);
            if (!extension.isEmpty() && !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
                return ValidationResult.invalid("不支持的文件类型: " + extension);
            }
        }

        return ValidationResult.valid();
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot + 1) : "";
    }

    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String error;

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
    }
}