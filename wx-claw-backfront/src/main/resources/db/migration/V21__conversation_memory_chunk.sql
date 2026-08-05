CREATE TABLE conversation_memory_chunk (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(36),
    chunk_text TEXT NOT NULL,
    embedding LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    KEY idx_memory_user_expire (tenant_id, user_id, expires_at)
);
