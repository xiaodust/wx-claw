package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.agent.tools.shared.TextSanitizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 聊天请求构建器
 * 负责拼装 prompt、裁剪历史上下文
 */
@Component
public class ChatRequestBuilder {

    /**
     * 构建发送给 LLM 的请求文本
     * @param userMessage 用户当前消息
     * @param historyMessages 历史消息列表
     * @param maxContextChars 最大上下文字符数
     * @param maxMessageChars 单条消息最大字符数
     * @return 构建好的请求文本
     */
    public String buildRequestText(String userMessage,
                                    List<AiMessage> historyMessages,
                                    int maxContextChars,
                                    int maxMessageChars) {
        String sanitizedUserMessage = TextSanitizer.sanitizeForPrompt(userMessage);
        if (sanitizedUserMessage != null && !sanitizedUserMessage.isBlank()) {
            sanitizedUserMessage = sanitizedUserMessage.trim();
        }

        List<AiMessage> sortedHistory = new ArrayList<>();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            historyMessages.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(AiMessage::getMessageSeq, Comparator.nullsLast(Integer::compareTo)))
                    .forEach(sortedHistory::add);
        }

        List<String> picked = new ArrayList<>();
        int totalChars = 0;
        int dropped = 0;

        if (!sortedHistory.isEmpty()) {
            for (int i = sortedHistory.size() - 1; i >= 0; i--) {
                String content = TextSanitizer.sanitizeForPrompt(sortedHistory.get(i).getContent());
                if (content == null || content.isBlank()) {
                    continue;
                }
                String trimmed = content.trim();
                if (maxMessageChars > 0 && trimmed.length() > maxMessageChars) {
                    trimmed = trimmed.substring(0, maxMessageChars) + "...";
                }
                int addLen = trimmed.length() + 1;
                if (maxContextChars > 0 && totalChars + addLen > maxContextChars) {
                    dropped = i + 1;
                    break;
                }
                picked.add(trimmed);
                totalChars += addLen;
            }
        }

        String truncationNote = dropped > 0 ? "(历史较长，已省略前 " + dropped + " 条消息)\n" : "";
        int noteLen = truncationNote.isEmpty() ? 0 : truncationNote.length();

        StringBuilder sb = new StringBuilder();
        if (!truncationNote.isEmpty()) {
            if (maxContextChars > 0 && noteLen > maxContextChars) {
                truncationNote = truncationNote.substring(0, maxContextChars);
            }
            sb.append(truncationNote);
        }

        for (int i = picked.size() - 1; i >= 0; i--) {
            String line = picked.get(i);
            if (maxContextChars > 0 && sb.length() + line.length() + 1 > maxContextChars) {
                break;
            }
            sb.append(line).append("\n");
        }

        if (sanitizedUserMessage != null && !sanitizedUserMessage.isBlank()) {
            String trimmed = sanitizedUserMessage;
            if (maxMessageChars > 0 && trimmed.length() > maxMessageChars) {
                trimmed = trimmed.substring(0, maxMessageChars) + "...";
            }
            if (maxContextChars > 0 && sb.length() + trimmed.length() > maxContextChars) {
                int allowed = Math.max(0, maxContextChars - sb.length());
                if (allowed > 0) {
                    sb.append(trimmed, 0, Math.min(allowed, trimmed.length()));
                }
                return sb.toString();
            }
            sb.append(trimmed);
        }

        return sb.toString();
    }
}
