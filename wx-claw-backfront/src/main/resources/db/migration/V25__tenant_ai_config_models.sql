-- 用户可自定义的模型选择：聊天支持服务商（provider）+ 模型；图片/视频(Ark)支持模型
ALTER TABLE tenant_ai_config
    ADD COLUMN chat_provider VARCHAR(32) NULL,
    ADD COLUMN chat_base_url  VARCHAR(512) NULL,
    ADD COLUMN chat_model     VARCHAR(128) NULL,
    ADD COLUMN image_model    VARCHAR(128) NULL,
    ADD COLUMN video_model    VARCHAR(128) NULL;
