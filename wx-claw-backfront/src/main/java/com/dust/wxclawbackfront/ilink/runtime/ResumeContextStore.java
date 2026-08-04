package com.dust.wxclawbackfront.ilink.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.core.context.ConversationContext;
import com.github.wechat.ilink.sdk.core.context.ContextKey;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResumeContext 持久化存储
 * 用于在服务重启后恢复 ILinkClient 的登录上下文和所有用户的 context token
 */
@Slf4j
@Component
public class ResumeContextStore {

    private static final String CONTEXT_FILE_PREFIX = ".ilink-resume-context-";
    private static final String MODE_DB = "db";
    private final ObjectMapper objectMapper;
    private final TenantBotRepository tenantBotRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${wxclaw.ilink.resume-context-cleanup.enabled:true}")
    private boolean orphanCleanupEnabled;

    /**
     * 存储后端：db（MySQL，多实例共享，默认）/ file（本地文件，单机兼容）。
     */
    @Value("${wxclaw.ilink.resume-context-store:db}")
    private String storageMode;

    public ResumeContextStore(TenantBotRepository tenantBotRepository, JdbcTemplate jdbcTemplate) {
        this.tenantBotRepository = tenantBotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        // 忽略 JSON 中未知的字段，避免格式升级时报错
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
                false
        );
    }

    /**
     * 保存 ResumeContext（DB upsert 或本地文件）。
     */
    public void save(BotRuntimeKey key, ResumeContext context) {
        if (context == null) {
            log.warn("ResumeContext 为空，跳过保存");
            return;
        }

        try {
            // 转换为可序列化的 DTO
            ResumeContextDTO dto = toDTO(context);
            if (useDb()) {
                String json = objectMapper.writeValueAsString(dto);
                jdbcTemplate.update("""
                                INSERT INTO ilink_resume_context (tenant_id, bot_id, payload, updated_at)
                                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                                ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP
                                """,
                        key.tenantId(), key.botId(), json);
                log.info("ResumeContext 已保存到 DB: tenantId={}, botId={}，包含 {} 个用户的 context token",
                        key.tenantId(), key.botId(), dto.getConversationContexts().size());
            } else {
                File file = contextFile(key);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, dto);
                log.info("ResumeContext 已保存到文件: {}，包含 {} 个用户的 context token",
                        file.getAbsolutePath(), dto.getConversationContexts().size());
            }
        } catch (Exception e) {
            log.error("保存 ResumeContext 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 加载 ResumeContext（DB 优先，DB 无记录时兼容迁移本地文件）。
     */
    public ResumeContext load(BotRuntimeKey key) {
        if (useDb()) {
            try {
                List<String> payloads = jdbcTemplate.queryForList("""
                                SELECT payload FROM ilink_resume_context
                                WHERE tenant_id = ? AND bot_id = ?
                                """, String.class, key.tenantId(), key.botId());
                if (!payloads.isEmpty()) {
                    ResumeContextDTO dto = objectMapper.readValue(payloads.get(0), ResumeContextDTO.class);
                    ResumeContext context = fromDTO(dto);
                    log.info("ResumeContext 已从 DB 加载: tenantId={}, botId={}，包含 {} 个用户的 context token",
                            key.tenantId(), key.botId(), dto.getConversationContexts().size());
                    return context;
                }
                // 兼容迁移：本地旧文件存在则读取并写入 DB
                File file = contextFile(key);
                if (file.exists()) {
                    ResumeContext legacy = loadFromFile(key);
                    if (legacy != null) {
                        save(key, legacy);
                    }
                    return legacy;
                }
                log.info("ResumeContext 不存在（DB），将从空状态启动: tenantId={}, botId={}",
                        key.tenantId(), key.botId());
                return null;
            } catch (Exception e) {
                log.error("从 DB 加载 ResumeContext 失败，将从空状态启动: {}", e.getMessage(), e);
                return null;
            }
        }
        return loadFromFile(key);
    }

    private ResumeContext loadFromFile(BotRuntimeKey key) {
        File file = contextFile(key);
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

    public boolean exists(BotRuntimeKey key) {
        if (useDb()) {
            Integer count = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM ilink_resume_context
                            WHERE tenant_id = ? AND bot_id = ?
                            """, Integer.class, key.tenantId(), key.botId());
            return count != null && count > 0;
        }
        return contextFile(key).exists();
    }

    /**
     * 删除持久化记录（DB 行或本地文件）。
     */
    public void delete(BotRuntimeKey key) {
        if (useDb()) {
            int deleted = jdbcTemplate.update("""
                            DELETE FROM ilink_resume_context
                            WHERE tenant_id = ? AND bot_id = ?
                            """, key.tenantId(), key.botId());
            if (deleted > 0) {
                log.info("ResumeContext 记录已删除（DB）: tenantId={}, botId={}", key.tenantId(), key.botId());
            }
            return;
        }
        File file = contextFile(key);
        if (file.exists() && file.delete()) {
            log.info("ResumeContext 文件已删除: {}", file.getAbsolutePath());
        }
    }

    /**
     * 定时清理不再属于任何 ACTIVE Bot 的 ResumeContext 记录/文件，
     * 防止 Bot 被删除或停用后登录上下文永久残留。
     */
    @Scheduled(cron = "${wxclaw.ilink.resume-context-cleanup.cron:0 45 3 * * ?}")
    public void cleanupOrphanedFiles() {
        if (!orphanCleanupEnabled) {
            return;
        }
        List<TenantBot> activeBots =
                tenantBotRepository.findByChannelAndStatus("ILINK", "ACTIVE");
        if (activeBots.isEmpty()) {
            log.warn("没有 ACTIVE ILink Bot，跳过 ResumeContext 孤儿清理，避免误删");
            return;
        }
        if (useDb()) {
            int deleted = jdbcTemplate.update("""
                            DELETE c FROM ilink_resume_context c
                            LEFT JOIN tenant_bot b
                              ON b.channel = 'ILINK' AND b.status = 'ACTIVE'
                             AND b.tenant_id = c.tenant_id AND b.bot_id = c.bot_id
                            WHERE b.id IS NULL
                            """);
            if (deleted > 0) {
                log.info("已清理 {} 条孤儿 ResumeContext 记录（DB）", deleted);
            }
            return;
        }
        Set<String> expectedFileNames = activeBots.stream()
                .map(bot -> contextFileName(bot.getTenantId(), bot.getBotId()))
                .collect(Collectors.toSet());
        File dir = new File(".");
        File[] files = dir.listFiles((d, name) ->
                name.startsWith(CONTEXT_FILE_PREFIX) && name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!expectedFileNames.contains(file.getName()) && file.delete()) {
                log.info("已清理孤儿 ResumeContext 文件: {}", file.getName());
            }
        }
    }

    private File contextFile(BotRuntimeKey key) {
        return new File(contextFileName(key.tenantId(), key.botId()));
    }

    private boolean useDb() {
        return MODE_DB.equalsIgnoreCase(storageMode);
    }

    private String contextFileName(String tenantId, String botId) {
        return CONTEXT_FILE_PREFIX + sanitize(tenantId) + "-" + sanitize(botId) + ".json";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
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
