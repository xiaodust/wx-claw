package com.dust.wxclawbackfront.bot.agent.llm.chat.file;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileContentExtractorTest {
    private final FileContentExtractor extractor = new FileContentExtractor();

    @Test
    void completeExtractionIncludesDocxTablesAndContentPastChatLimit() throws Exception {
        byte[] documentBytes;
        String longProjectDescription = "项目成果".repeat(2200);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("个人简历");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("工作经历");
            table.getRow(0).getCell(1).setText("某科技公司 Java 工程师");
            document.createParagraph().createRun().setText(longProjectDescription);
            document.write(output);
            documentBytes = output.toByteArray();
        }

        FileContentExtractor.FileExtractResult chatResult = extractor.extract(documentBytes, "resume.docx");
        FileContentExtractor.FileExtractResult completeResult = extractor.extractComplete(documentBytes, "resume.docx");

        assertThat(completeResult.content()).contains("工作经历", "某科技公司 Java 工程师");
        assertThat(completeResult.content().length()).isGreaterThan(chatResult.content().length());
        assertThat(completeResult.content()).doesNotContain("...[内容过长，已截断]");
    }
}
