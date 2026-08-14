-- 对话支持多服务商后，视频生成（Seedance）需要独立的火山方舟 Key：
-- 未配置时复用火山方舟对话 Key（对话为 ark 时），否则回退后端默认
ALTER TABLE tenant_ai_config ADD COLUMN video_api_key TEXT NULL;
