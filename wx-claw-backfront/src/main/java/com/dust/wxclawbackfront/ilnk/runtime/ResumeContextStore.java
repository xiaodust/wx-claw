package com.dust.wxclawbackfront.ilnk.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.core.context.ConversationContext;
import com.github.wechat.ilink.sdk.core.context.ContextKey;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ResumeContext 持久化存储
 * 用于在服务重启后恢复 ILinkClient 的登录上下文和所有用户的 context token
 */
@Slf4j
@Component
public class ResumeContextStore {

    private static final String CONTEXT_FILE_PATH = ".ilink-resume-context.json";
    private final ObjectMapper objectMapper;

    public ResumeContextStore() {
        this.objectMapper = new ObjectMapper();
        // 忽略 JSON 中未知的字段，避免格式升级时报错
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false
        );
    }

    /**
     * 保存 ResumeContext 到本地文件
     */
    public void save(ResumeContext context) {
        if (context == null) {
            log.warn("ResumeContext 为空，跳过保存");
            return;
        }

        try {
            // 转换为可序列化的 DTO
            ResumeContextDTO dto = toDTO(context);
            
            File file = new File(CONTEXT_FILE_PATH);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
            log.info("ResumeContext 已保存到文件: {}，包含 {} 个用户的 context token", 
                    file.getAbsolutePath(), dto.getConversationContexts().size());
        } catch (Exception e) {
            log.error("保存 ResumeContext 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从本地文件加载 ResumeContext
     */
    public ResumeContext load() {
        File file = new File(CONTEXT_FILE_PATH);
        if (!file.exists()) {
            log.info("ResumeContext 文件不存在，将从空状态启动: {}", file.getAbsolutePath());
            return null;
        }

        try {
            ResumeContextDTO dto = objectMapper.readValue(file, ResumeContextDTO.class);
            ResumeContext context = fromDTO(dto);
            log.info("ResumeContext 已从文件加载: {}，包含 {} 个用户的 context token", 
                    file.getAbsolutePath(), dto.getConversationContexts().size());
            return context;
        } catch (IOException e) {
            log.error("加载 ResumeContext 失败，将从空状态启动: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除持久化文件
     */
    public void delete() {
        File file = new File(CONTEXT_FILE_PATH);
        if (file.exists() && file.delete()) {
            log.info("ResumeContext 文件已删除: {}", file.getAbsolutePath());
        }
    }

    private ResumeContextDTO toDTO(ResumeContext context) {
        ResumeContextDTO dto = new ResumeContextDTO();
        
        LoginContext loginCtx = context.getLoginContext();
        if (loginCtx != null) {
            LoginContextDTO loginDTO = new LoginContextDTO();
            loginDTO.setBotToken(loginCtx.getBotToken());
            loginDTO.setUserId(loginCtx.getUserId());
            loginDTO.setBotId(loginCtx.getBotId());
            loginDTO.setBaseUrl(loginCtx.getBaseUrl());
            dto.setLoginContext(loginDTO);
        }
        
        dto.setUpdatesCursor(context.getUpdatesCursor());
        
        List<ConversationContextDTO> convDTOs = new ArrayList<>();
        for (ConversationContext conv : context.getConversationContexts()) {
            ConversationContextDTO convDTO = new ConversationContextDTO();
            ContextKey key = conv.getKey();
            if (key != null) {
                convDTO.setBotId(key.getBotId());
                convDTO.setUserId(key.getUserId());
            }
            convDTO.setLatestContextToken(conv.getLatestContextToken());
            convDTO.setTypingTicket(conv.getTypingTicket());
            convDTO.setLastUpdatedAt(conv.getLastUpdatedAt());
            convDTO.setSourceMessageId(conv.getSourceMessageId());
            convDTO.setSourceMessageTime(conv.getSourceMessageTime());
            convDTOs.add(convDTO);
        }
        dto.setConversationContexts(convDTOs);
        
        return dto;
    }

    private ResumeContext fromDTO(ResumeContextDTO dto) {
        LoginContextDTO loginDTO = dto.getLoginContext();
        if (loginDTO == null) {
            return null;
        }
        
        LoginContext loginContext = new LoginContext(
                loginDTO.getBotToken(),
                loginDTO.getUserId(),
                loginDTO.getBotId(),
                loginDTO.getBaseUrl()
        );
        
        ResumeContext.Builder builder = ResumeContext.builder(loginContext);
        builder.updatesCursor(dto.getUpdatesCursor());
        
        List<ConversationContext> convs = new ArrayList<>();
        if (dto.getConversationContexts() != null) {
            for (ConversationContextDTO convDTO : dto.getConversationContexts()) {
                if (convDTO.getBotId() == null || convDTO.getUserId() == null) {
                    continue;
                }
                ContextKey key = new ContextKey(convDTO.getBotId(), convDTO.getUserId());
                ConversationContext conv = new ConversationContext(key);
                conv.setLatestContextToken(convDTO.getLatestContextToken());
                conv.setTypingTicket(convDTO.getTypingTicket());
                if (convDTO.getSourceMessageId() != null && convDTO.getSourceMessageTime() != null) {
                    conv.updateContextToken(
                            convDTO.getLatestContextToken(),
                            convDTO.getSourceMessageId(),
                            convDTO.getSourceMessageTime()
                    );
                }
                convs.add(conv);
            }
        }
        
        // ResumeContext.Builder 需要 Map<String, ConversationContext>
        java.util.Map<String, ConversationContext> convMap = new java.util.LinkedHashMap<>();
        for (ConversationContext conv : convs) {
            if (conv.getKey() != null && conv.getKey().getUserId() != null) {
                convMap.put(conv.getKey().getUserId(), conv);
            }
        }
        builder.conversationContexts(convMap);
        
        return builder.build();
    }

    @Data
    private static class ResumeContextDTO {
        private LoginContextDTO loginContext;
        private String updatesCursor;
        private List<ConversationContextDTO> conversationContexts;
    }

    @Data
    private static class LoginContextDTO {
        private String botToken;
        private String userId;
        private String botId;
        private String baseUrl;
    }

    @Data
    private static class ConversationContextDTO {
        private String botId;
        private String userId;
        private String latestContextToken;
        private String typingTicket;
        private long lastUpdatedAt;
        private Long sourceMessageId;
        private Long sourceMessageTime;
    }
}
