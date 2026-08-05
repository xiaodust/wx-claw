CREATE TABLE ai_conversation_summary (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL UNIQUE,
    summary_text TEXT NOT NULL,
    last_summarized_seq INT NOT NULL DEFAULT 0,
    summary_version INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL,
    KEY idx_conv_summary_tenant_updated (tenant_id, updated_at)
);

ALTER TABLE user_profile
    ADD COLUMN confidence DECIMAL(3,2) NOT NULL DEFAULT 0.50,
    ADD COLUMN expires_at TIMESTAMP NULL;

ALTER TABLE ai_conversation
    ADD COLUMN last_memory_extract_seq INT NULL;
