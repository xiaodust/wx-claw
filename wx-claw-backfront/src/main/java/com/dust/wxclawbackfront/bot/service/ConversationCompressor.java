package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史压缩服务
 * 采用两阶段压缩策略：分段摘要 + 汇总总结
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCompressor {

    private final PlainTextLlmService plainTextLlmService;

    private static final int MAX_SUMMARY_CHARS = 40000;
    private static final int CHUNK_SIZE = 20; // 每 20 条消息为一个分段
    private static final int MAX_CHUNK_SUMMARY_CHARS = 1000; // 每段摘要最多 1000 字

    /**
     * 两阶段智能压缩：先分段摘要，再汇总
     * 适用于超长对话，能保留核心信息同时大幅压缩 token
     */
    public String compressWithSummary(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // 少于 30 条消息，直接用简单压缩
        if (messages.size() <= 30) {
            return smartCompress(messages);
        }

        log.info("对话消息较多({})，启用两阶段压缩", messages.size());

        // 第一阶段：分段摘要
        List<String> chunkSummaries = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, messages.size());
            List<AiMessage> chunk = messages.subList(i, end);
            
            String chunkText = formatMessagesForSummary(chunk);
            String summary = summarizeChunk(chunkText, i / CHUNK_SIZE + 1);
            
            if (summary != null && !summary.isBlank()) {
                chunkSummaries.add(summary);
            }
        }

        // 第二阶段：汇总所有分段摘要
        StringBuilder result = new StringBuilder();
        result.append(String.format("【对话片段总数：%d，共 %d 条消息】\n\n", 
                chunkSummaries.size(), messages.size()));
        
        for (int i = 0; i < chunkSummaries.size(); i++) {
            result.append(String.format("## 片段 %d\n%s\n\n", i + 1, chunkSummaries.get(i)));
        }

        return result.toString();
    }

    /**
     * 对单个分段进行 AI 摘要
     */
    private String summarizeChunk(String chunkText, int chunkIndex) {
        try {
            String prompt = String.format(
                    "请对以下对话片段提取关键信息，输出简洁摘要（不超过%d字）：\n\n" +
                    "要求：\n" +
                    "1. 提取用户的核心问题、需求或话题\n" +
                    "2. 总结 AI 提供的关键信息或解决方案\n" +
                    "3. 保留重要的时间、数字、人名、地点等实体\n" +
                    "4. 用分点格式输出，每点不超过一行\n" +
                    "5. 省略寒暄、确认等无关紧要的内容\n\n" +
                    "对话内容：\n%s",
                    MAX_CHUNK_SUMMARY_CHARS, chunkText
            );

            String summary = plainTextLlmService.chat(prompt);
            
            if (summary != null && summary.length() > MAX_CHUNK_SUMMARY_CHARS) {
                summary = summary.substring(0, MAX_CHUNK_SUMMARY_CHARS) + "...";
            }
            
            return summary;

        } catch (Exception e) {
            log.warn("分段摘要失败，使用降级策略: chunkIndex={}, error={}", chunkIndex, e.getMessage());
            // 降级：直接截断返回
            return chunkText.length() > MAX_CHUNK_SUMMARY_CHARS 
                    ? chunkText.substring(0, MAX_CHUNK_SUMMARY_CHARS) + "..." 
                    : chunkText;
        }
    }

    /**
     * 将消息列表格式化为可读文本
     */
    private String formatMessagesForSummary(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            String role = msg.getMessageType() == 0 ? "用户" : "AI";
            String content = msg.getContent().trim();
            
            // 单条消息最多 500 字
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            
            sb.append(String.format("[%s]: %s\n", role, content));
        }
        return sb.toString();
    }

    /**
     * 智能采样：优先保留用户消息，AI回复做更激进的截断
     * 适用于消息较少的场景（< 30 条）
     */
    public String smartCompress(List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;
        int userMsgCount = 0;
        int aiMsgCount = 0;

        for (AiMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }

            boolean isUser = msg.getMessageType() == 0;
            String role = isUser ? "用户" : "AI";
            String content = msg.getContent().trim();

            // 用户消息优先保留，AI回复做更激进的截断
            int maxLen = isUser ? 500 : 250;
            if (content.length() > maxLen) {
                content = content.substring(0, maxLen) + "...";
            }

            String line = String.format("[%s]: %s\n", role, content);

            if (totalChars + line.length() > MAX_SUMMARY_CHARS) {
                sb.append(String.format("...(共 %d 条用户消息, %d 条AI回复)\n", userMsgCount, aiMsgCount));
                break;
            }

            sb.append(line);
            totalChars += line.length();

            if (isUser) {
                userMsgCount++;
            } else {
                aiMsgCount++;
            }
        }

        return sb.toString();
    }

    /**
     * 简单压缩（废弃，仅保留兼容性）
     */
    @Deprecated
    public String compressMessages(List<AiMessage> messages) {
        return smartCompress(messages);
    }

    /**
     * 分段压缩（废弃，仅保留兼容性）
     */
    @Deprecated
    public List<String> compressIntoChunks(List<AiMessage> messages, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return chunks;
        }

        for (int i = 0; i < messages.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, messages.size());
            List<AiMessage> chunk = messages.subList(i, end);
            String compressed = smartCompress(chunk);
            if (!compressed.isBlank()) {
                chunks.add(compressed);
            }
        }

        return chunks;
    }
}
