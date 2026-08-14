package com.dust.wxclawbackfront.tenancy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户级 LLM 配置。
 *
 * <p>用户可在使用页面配置自己的 API Key，覆盖后端默认 key。key 以明文保存
 * （调用方 LLM 服务需要原文），读取/回显一律脱敏，不写入日志。</p>
 */
@Data
@Entity
@Table(name = "tenant_ai_config")
public class TenantAiConfig {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    /** 聊天服务商：ark / openai / deepseek / zhipu / custom */
    @Column(name = "chat_provider", length = 32)
    private String chatProvider;

    /** 聊天服务商对应 baseUrl（custom 时由用户输入） */
    @Column(name = "chat_base_url", length = 512)
    private String chatBaseUrl;

    /** 聊天模型 */
    @Column(name = "chat_model", length = 128)
    private String chatModel;

    /** 图片生成（SiliconFlow） */
    @Column(name = "image_api_key", columnDefinition = "TEXT")
    private String imageApiKey;

    /** 图片生成模型 */
    @Column(name = "image_model", length = 128)
    private String imageModel;

    /** 视频生成（阿里云通义万相 DashScope） */
    @Column(name = "video_dashscope_api_key", columnDefinition = "TEXT")
    private String videoDashscopeApiKey;

    /** 语音合成（火山引擎 TTS） */
    @Column(name = "tts_api_key", columnDefinition = "TEXT")
    private String ttsApiKey;

    /** 视频生成（火山方舟 Seedance）模型 */
    @Column(name = "video_model", length = 128)
    private String videoModel;

    /** 视频生成（火山方舟 Seedance）专用 Key；未配置时复用对话 Ark Key */
    @Column(name = "video_api_key", columnDefinition = "TEXT")
    private String videoApiKey;

    /** 联网搜索（博查） */
    @Column(name = "search_api_key", columnDefinition = "TEXT")
    private String searchApiKey;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
