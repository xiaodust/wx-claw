package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.orchestrator.AgentOrchestrator;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.ilink.ILinkUserInput;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Agent 响应处理器
 * 负责调用 Agent 编排器并处理媒体响应
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentResponseProcessor {

    private final AgentOrchestrator agentOrchestrator;
    private final UserMemoryService userMemoryService;
    private final ILinkMessageSender messageSender;

    /**
     * 使用 Agent 编排器处理消息
     * @return 回复文本，如果已发送媒体则返回 null
     */
    public String process(ILinkUserInput userInput, List<AiMessage> historyMessages,
                         String userId, String sessionId) {
        log.info("开始 Agent 处理: userId={}, sessionId={}", userId, sessionId);

        // 构建 Agent 上下文
        List<UserProfile> profiles = userMemoryService.getProfiles(userId);

        AgentContext context = AgentContext.builder()
                .userId(userId)
                .historyMessages(historyMessages)
                .userProfiles(profiles)
                .userText(userInput.getDisplayText())
                .build();

        // 调用 Agent 编排器
        AgentResult result = agentOrchestrator.orchestrate(userInput.getPromptText(), context);

        // 处理 Agent 结果
        if (!result.isSuccess()) {
            log.warn("Agent 处理失败: userId={}, error={}", userId, result.getErrorMessage());
            throw new RuntimeException(result.getErrorMessage());
        }

        log.info("Agent 处理完成: userId={}, hasMedia={}, mediaType={}", userId, result.hasMedia(), result.getMediaType());

        // 如果有媒体数据（图片/音频/视频），发送媒体后返回 null
        if (result.hasMedia()) {
            return handleMediaResponse(userId, result);
        }

        return result.getReplyText();
    }

    /**
     * 处理媒体响应
     * @return null 如果媒体发送成功，否则返回降级的文本回复
     */
    private String handleMediaResponse(String userId, AgentResult result) {
        String mediaType = result.getMediaType();

        if (mediaType != null && mediaType.startsWith("image/")) {
            return handleImageResponse(userId, result);
        } else if (mediaType != null && mediaType.startsWith("audio/")) {
            return handleAudioResponse(userId, result);
        } else if (mediaType != null && mediaType.startsWith("video/")) {
            return handleVideoResponse(userId, result);
        }

        return result.getReplyText();
    }

    private String handleImageResponse(String userId, AgentResult result) {
        try {
            messageSender.sendImage(userId, result.getMediaBytes(), result.getMediaFileName(), result.getReplyText());
            log.info("Agent 图片已发送: userId={}", userId);
            return null;
        } catch (Exception ex) {
            log.warn("发送图片失败，降级为文本回复: {}", ex.getMessage());
            return result.getReplyText();
        }
    }

    private String handleAudioResponse(String userId, AgentResult result) {
        try {
            messageSender.sendFile(userId, result.getMediaBytes(), result.getMediaFileName(), "已生成音频文件，请查收。");
            log.info("Agent 音频已发送: userId={}", userId);
            return null;
        } catch (Exception ex) {
            log.warn("发送音频文件失败，降级为文本回复: {}", ex.getMessage());
            return result.getReplyText();
        }
    }

    private String handleVideoResponse(String userId, AgentResult result) {
        try {
            messageSender.sendVideo(userId, result.getMediaBytes(), result.getMediaFileName(), null, "已生成视频，请查收。");
            log.info("Agent 视频已发送: userId={}", userId);
            return null;
        } catch (Exception ex) {
            log.warn("发送视频失败，降级为文件发送: {}", ex.getMessage());
            try {
                messageSender.sendFile(userId, result.getMediaBytes(), result.getMediaFileName(), "已生成视频，请查收。");
                return null;
            } catch (Exception sendFileEx) {
                log.warn("发送视频文件也失败，降级为文本回复: {}", sendFileEx.getMessage());
                return result.getReplyText();
            }
        }
    }
}
