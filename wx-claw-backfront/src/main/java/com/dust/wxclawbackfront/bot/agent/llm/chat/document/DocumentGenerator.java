package com.dust.wxclawbackfront.bot.agent.llm.chat.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 文档生成器
 * 将长文本生成为 txt 或 markdown 文档
 */
@Slf4j
@Component
public class DocumentGenerator {

    private final int configuredTextThreshold;

    public DocumentGenerator(@Value("${wxclaw.ai.document.threshold:2000}") int textThreshold) {
        this.configuredTextThreshold = Math.max(1, textThreshold);
    }

    private static final int TEXT_THRESHOLD = 500; // 超过500字生成文档

    // Markdown 语法清理正则
    private static final Pattern MD_HEADER = Pattern.compile("^(#{1,6})\\s+", Pattern.MULTILINE);
    private static final Pattern MD_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern MD_ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern MD_INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern MD_STRIKETHROUGH = Pattern.compile("~~(.+?)~~");
    private static final Pattern MD_HRULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$", Pattern.MULTILINE);
    private static final Pattern MD_TABLE_SEP = Pattern.compile("^\\|[-\\s|:]+\\|\\s*$", Pattern.MULTILINE);
    private static final Pattern MD_BLOCKQUOTE = Pattern.compile("^>\\s?", Pattern.MULTILINE);
    private static final Pattern MD_CODE_BLOCK = Pattern.compile("```[\\w]*\\n?([\\s\\S]*?)```");
    private static final Pattern MD_LIST_BULLET = Pattern.compile("^(\\s*)[-*+]\\s+", Pattern.MULTILINE);
    private static final Pattern MD_LIST_NUM = Pattern.compile("^(\\s*)\\d+\\.\\s+", Pattern.MULTILINE);
    private static final Pattern MD_HTML_TAG = Pattern.compile("<[^>]+>");

    /**
     * 检查是否需要生成文档
     */
    public boolean shouldGenerateDocument(String text) {
        return text != null && text.length() > configuredTextThreshold;
    }

    /**
     * 生成文档
     * @param content 文本内容
     * @param format 文档格式：txt 或 markdown
     * @return 文档结果
     */
    public DocumentResult generate(String content, String format) {
        if (content == null || content.isBlank()) {
            return new DocumentResult(null, null, null, "内容为空");
        }

        boolean isMarkdown = "markdown".equalsIgnoreCase(format) || "md".equalsIgnoreCase(format);
        String fileExtension = isMarkdown ? "md" : "txt";
        String formattedContent = isMarkdown ? formatAsMarkdown(content) : formatAsPlainText(content);
        String fileName = generateFileName(formattedContent, fileExtension);

        byte[] bytes = formattedContent.getBytes(StandardCharsets.UTF_8);
        String contentType = isMarkdown ? "text/markdown" : "text/plain";

        log.info("生成文档: fileName={}, format={}, size={}bytes", fileName, format, bytes.length);

        return new DocumentResult(fileName, bytes, contentType, null);
    }

    /**
     * 格式化为纯文本 - 剥离所有 Markdown 语法
     */
    private String formatAsPlainText(String content) {
        String text = cleanCodeBlockMarkers(content);

        // 先处理代码块（保留内容，去除围栏）
        text = MD_CODE_BLOCK.matcher(text).replaceAll("$1");

        // 剥离 markdown 语法
        text = MD_IMAGE.matcher(text).replaceAll("$1");           // ![alt](url) → alt
        text = MD_LINK.matcher(text).replaceAll("$1 ($2)");       // [text](url) → text (url)
        text = MD_HEADER.matcher(text).replaceAll("");             // ### → 移除
        text = MD_BOLD.matcher(text).replaceAll("$1");             // **bold** → bold
        text = MD_ITALIC.matcher(text).replaceAll("$1");           // *italic* → italic
        text = MD_STRIKETHROUGH.matcher(text).replaceAll("$1");    // ~~del~~ → del
        text = MD_INLINE_CODE.matcher(text).replaceAll("$1");      // `code` → code
        text = MD_BLOCKQUOTE.matcher(text).replaceAll("");         // > 引用 → 引用
        text = MD_HRULE.matcher(text).replaceAll("");              // --- → 移除
        text = MD_TABLE_SEP.matcher(text).replaceAll("");          // |---|---| → 移除
        // 列表项：保留内容，用缩进标识
        text = MD_LIST_BULLET.matcher(text).replaceAll("$1- ");
        text = MD_LIST_NUM.matcher(text).replaceAll("$1");         // 1. → 移除编号
        text = MD_HTML_TAG.matcher(text).replaceAll("");           // HTML 标签 → 移除

        // 表格行：将 | 分隔转为空格分隔
        text = cleanTableRows(text);

        // 清理多余空行（超过2个连续空行压缩为2个）
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim() + "\n";
    }

    /**
     * 清理表格行：| col1 | col2 | → col1  col2
     */
    private String cleanTableRows(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // 去掉首尾 |，用多个空格分隔列
                String inner = trimmed.substring(1, trimmed.length() - 1);
                String[] cols = inner.split("\\|");
                StringBuilder row = new StringBuilder();
                for (int i = 0; i < cols.length; i++) {
                    if (i > 0) row.append("    ");
                    row.append(cols[i].trim());
                }
                sb.append(row).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 格式化为 Markdown
     */
    private String formatAsMarkdown(String content) {
        String cleanedContent = cleanCodeBlockMarkers(content);

        StringBuilder sb = new StringBuilder();

        // 添加文档头
        sb.append("# AI 对话回复\n\n");
        sb.append("---\n\n");

        // 处理内容，保留原有的换行和格式
        String[] lines = cleanedContent.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 代码块围栏：原样保留，切换状态
            if (trimmed.startsWith("```")) {
                sb.append(line).append("\n");
                inCodeBlock = !inCodeBlock;
                continue;
            }

            // 代码块内部：原样保留，不做任何处理
            if (inCodeBlock) {
                sb.append(line).append("\n");
                continue;
            }

            // 空行
            if (trimmed.isEmpty()) {
                sb.append("\n");
            }
            // 标题、列表、引用、表格、分隔线等 markdown 元素：原样保留
            else if (trimmed.startsWith("#")
                    || trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")
                    || trimmed.startsWith("> ")
                    || trimmed.startsWith("|")
                    || trimmed.matches("^\\d+[.、].*")
                    || trimmed.matches("^[-*_]{3,}$")) {
                sb.append(line).append("\n");
            }
            // 普通文本：保持原样换行
            else {
                sb.append(line).append("\n");
            }
        }

        // 添加文档尾
        sb.append("\n---\n");
        sb.append("*Generated by WX-Claw AI*\n");

        return sb.toString();
    }

    /**
     * 清理 AI 回复中的代码块标记
     * 例如：```markdown ... ``` 或 ``` ... ```
     */
    private String cleanCodeBlockMarkers(String content) {
        if (content == null) {
            return "";
        }

        String result = content.trim();

        // 移除开头的 ```markdown 或 ```text 或 ```
        if (result.startsWith("```markdown")) {
            result = result.substring("```markdown".length());
        } else if (result.startsWith("```text")) {
            result = result.substring("```text".length());
        } else if (result.startsWith("```")) {
            result = result.substring("```".length());
        }
        result = result.trim();

        // 移除结尾的 ```
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        result = result.trim();

        return result;
    }

    /**
     * 生成文件名 - 根据内容自动提取标题
     */
    private String generateFileName(String content, String extension) {
        // 尝试从内容中提取标题
        String title = extractTitle(content);
        
        if (title != null && !title.isBlank()) {
            // 清理标题，移除特殊字符
            String cleanTitle = title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");
            
            // 限制长度
            if (cleanTitle.length() > 30) {
                cleanTitle = cleanTitle.substring(0, 30);
            }
            
            if (!cleanTitle.isBlank()) {
                return cleanTitle + "." + extension;
            }
        }
        
        // 如果无法提取标题，使用时间戳
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "ai_reply_" + timestamp + "." + extension;
    }

    /**
     * 从内容中提取标题
     */
    private String extractTitle(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 跳过空行
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 提取 markdown 标题（# 开头）
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim();
            }
            
            // 提取《》中的内容
            if (trimmed.contains("《") && trimmed.contains("》")) {
                int start = trimmed.indexOf("《");
                int end = trimmed.indexOf("》");
                if (end > start) {
                    return trimmed.substring(start + 1, end);
                }
            }
            
            // 如果第一行不为空且长度合适，作为标题
            if (trimmed.length() >= 3 && trimmed.length() <= 50) {
                return trimmed;
            }
        }
        
        return null;
    }

    /**
     * 文档结果
     */
    public record DocumentResult(String fileName, byte[] bytes, String contentType, String error) {
        public boolean isSuccess() {
            return error == null && bytes != null && bytes.length > 0;
        }
    }
}
