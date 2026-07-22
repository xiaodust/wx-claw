package com.dust.wxclawbackfront.ai.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 文件内容提取器
 * 支持从常见文件格式中提取文本内容
 */
@Slf4j
@Component
public class FileContentExtractor {

    private static final int MAX_TEXT_LENGTH = 8000; // 提取文本最大长度

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".csv", ".md", ".json", ".xml", ".html", ".htm",
            ".yml", ".yaml", ".properties", ".ini", ".cfg", ".conf",
            ".log", ".sql", ".sh", ".bat", ".py", ".js", ".ts",
            ".java", ".c", ".cpp", ".h", ".hpp", ".go", ".rs",
            ".rb", ".php", ".css", ".scss", ".less", ".vue", ".jsx", ".tsx"
    );

    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> WORD_EXTENSIONS = Set.of(".docx");
    private static final Set<String> EXCEL_EXTENSIONS = Set.of(".xlsx", ".xls");

    /**
     * 提取文件内容
     * @param fileBytes 文件字节
     * @param fileName 文件名
     * @return 提取结果
     */
    public FileExtractResult extract(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return FileExtractResult.failure("文件内容为空");
        }
        if (fileName == null || fileName.isBlank()) {
            return FileExtractResult.failure("文件名为空");
        }

        String ext = getExtension(fileName).toLowerCase();
        long start = System.currentTimeMillis();

        try {
            String content;
            if (PDF_EXTENSIONS.contains(ext)) {
                content = extractPdf(fileBytes);
            } else if (WORD_EXTENSIONS.contains(ext)) {
                content = extractWord(fileBytes);
            } else if (EXCEL_EXTENSIONS.contains(ext)) {
                content = extractExcel(fileBytes);
            } else if (TEXT_EXTENSIONS.contains(ext) || isLikelyText(fileBytes)) {
                content = extractText(fileBytes);
            } else {
                return FileExtractResult.failure("不支持的文件格式: " + ext);
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("文件内容提取完成: fileName={}, ext={}, length={}, 耗时={}ms",
                    fileName, ext, content != null ? content.length() : 0, elapsed);

            if (content == null || content.isBlank()) {
                return FileExtractResult.failure("未能从文件中提取到文本内容");
            }

            // 截断过长内容
            if (content.length() > MAX_TEXT_LENGTH) {
                content = content.substring(0, MAX_TEXT_LENGTH) + "\n\n...[内容过长，已截断]";
            }

            return FileExtractResult.success(content, ext);

        } catch (Exception e) {
            log.error("文件内容提取失败: fileName={}, error={}", fileName, e.getMessage(), e);
            return FileExtractResult.failure("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否支持该文件格式
     */
    public boolean isSupported(String fileName) {
        if (fileName == null) return false;
        String ext = getExtension(fileName).toLowerCase();
        return PDF_EXTENSIONS.contains(ext) || WORD_EXTENSIONS.contains(ext)
                || EXCEL_EXTENSIONS.contains(ext) || TEXT_EXTENSIONS.contains(ext);
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text;
        }
    }

    private String extractWord(byte[] bytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : document.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String extractExcel(byte[] bytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(bytes);
             Workbook workbook = WorkbookFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("【").append(sheet.getSheetName()).append("】\n");
                for (Row row : sheet) {
                    StringBuilder rowStr = new StringBuilder();
                    for (int c = 0; c <= row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (c > 0) rowStr.append("\t");
                        if (cell != null) {
                            rowStr.append(getCellStringValue(cell));
                        }
                    }
                    String line = rowStr.toString().trim();
                    if (!line.isBlank()) {
                        sb.append(line).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String extractText(byte[] bytes) {
        // 尝试 UTF-8，失败则尝试 GBK
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.contains("\uFFFD")) {
                return text;
            }
        } catch (Exception ignored) {
        }
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception ignored) {
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private String getCellStringValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }

    /**
     * 判断文件是否可能是文本文件（通过内容检测）
     */
    private boolean isLikelyText(byte[] bytes) {
        if (bytes.length == 0) return false;
        int checkLen = Math.min(bytes.length, 512);
        int nonTextCount = 0;
        for (int i = 0; i < checkLen; i++) {
            int b = bytes[i] & 0xFF;
            // 控制字符（除了常见的换行、制表等）
            if (b < 0x09 || (b > 0x0D && b < 0x20)) {
                nonTextCount++;
            }
        }
        // 如果非文本字符超过 30%，认为是二进制文件
        return (double) nonTextCount / checkLen < 0.3;
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    /**
     * 文件提取结果
     */
    public record FileExtractResult(String content, String fileType, String error) {
        public static FileExtractResult success(String content, String fileType) {
            return new FileExtractResult(content, fileType, null);
        }

        public static FileExtractResult failure(String error) {
            return new FileExtractResult(null, null, error);
        }

        public boolean isSuccess() {
            return error == null && content != null && !content.isBlank();
        }
    }
}
