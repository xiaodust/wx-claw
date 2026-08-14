package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.service.AiConversationCrudService;
import com.dust.wxclawbackfront.exception.WxClawException;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 错误处理器
 * 处理消息处理过程中的异常，保存错误信息并通知用户
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorHandler {

    private static final int MESSAGE_TYPE_ASSISTANT = 1;

    private final AiConversationCrudService crudService;
    private final ILinkMessageSender messageSender;

    /**
     * 处理错误
     */
    public void handle(Exception ex, String userId, String sessionId, Instant start) {
        int responseTime = (int) Duration.between(start, Instant.now()).toMillis();

        // 保存错误消息
        String errorMsg = ex instanceof WxClawException ? 
            "[" + ((WxClawException) ex).getCode() + "] " + ex.getMessage() : ex.getMessage();
        crudService.appendMessage(sessionId, MESSAGE_TYPE_ASSISTANT, null, null, responseTime, errorMsg);

        log.error("处理消息失败: userId={}, sessionId={}, error={}, errorCode={}", 
            userId, sessionId, ex.getMessage(), ex instanceof WxClawException ? ((WxClawException) ex).getCode() : "N/A", ex);

        // 通知用户
        try {
            String msgToUser = buildErrorMessage(ex);
            messageSender.sendText(userId, msgToUser);
        } catch (Exception ignored) {
        }
    }

    /**
     * 构建用户友好的错误消息
     */
    private String buildErrorMessage(Exception ex) {
        String msgToUser = "处理失败，请稍后再试。";
        String em = ex.getMessage() == null ? "" : ex.getMessage();

        if (ex instanceof WxClawException wxEx) {
            // 根据不同的错误代码返回相应的用户友好消息
            switch (wxEx.getCode()) {
                case "AGENT_PLANNING_ERROR":
                    msgToUser = "任务规划失败，请稍后再试。";
                    break;
                case "TOOL_EXECUTION_ERROR":
                    msgToUser = "工具执行失败，请稍后再试。";
                    break;
                default:
                    msgToUser = buildFallbackMessage(em);
                    break;
            }
        } else {
            msgToUser = buildFallbackMessage(em);
        }

        return msgToUser;
    }

    /**
     * 按异常文案生成用户友好的兜底消息（TTS / 生图等能力的常见失败原因）。
     */
    static String buildFallbackMessage(String em) {
        if (em.contains("TTS") || em.contains("tts") || em.contains("语音")) {
            if (em.contains("API Key 无效")) {
                return "语音功能配置的 API Key 无效，请在设置页核对语音合成 Key。";
            }
            if (em.contains("未配置")) {
                return "语音功能暂未配置完成，请稍后再试。";
            }
            if (em.contains("未开通") || em.contains("未授权")) {
                return "语音功能暂未开通（豆包语音服务未授权），请到火山引擎豆包语音控制台开通语音合成服务后重试。";
            }
            return "语音生成失败，请稍后再试。";
        }
        if (em.contains("生图")) {
            return "图片生成失败，请稍后再试。";
        }
        return "处理失败，请稍后再试。";
    }
}
