-- 租户级 AI 配置：允许用户配置自己的 LLM API Key，覆盖后端默认 key
CREATE TABLE tenant_ai_config (
    tenant_id  VARCHAR(64) NOT NULL PRIMARY KEY,
    api_key    TEXT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
