-- 用户可配置的多种 AI 能力 Key（api_key 保留为文本对话/理解，其余按能力独立）
ALTER TABLE tenant_ai_config
    ADD COLUMN image_api_key           TEXT NULL,
    ADD COLUMN video_api_key           TEXT NULL,
    ADD COLUMN video_dashscope_api_key TEXT NULL,
    ADD COLUMN tts_api_key             TEXT NULL,
    ADD COLUMN search_api_key          TEXT NULL;
