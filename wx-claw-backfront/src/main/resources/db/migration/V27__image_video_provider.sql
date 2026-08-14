-- 图片/视频生成支持多服务商（图片：siliconflow/ark/openai；视频：ark/openai，dashscope 走独立列）
ALTER TABLE tenant_ai_config
    ADD COLUMN image_provider VARCHAR(32) NULL,
    ADD COLUMN video_provider VARCHAR(32) NULL;
