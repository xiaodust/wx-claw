-- 火山方舟模型（对话/图片理解/视频 Seedance/向量）共用同一个 API Key，
-- 不再需要独立的 video_api_key 列
ALTER TABLE tenant_ai_config DROP COLUMN video_api_key;
