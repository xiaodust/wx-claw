CREATE TABLE llm_invocation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    bot_id VARCHAR(128),
    conversation_id VARCHAR(36),
    session_id VARCHAR(255),
    trace_id VARCHAR(64) NOT NULL,
    parent_invocation_id VARCHAR(36),
    sequence_no INTEGER NOT NULL,
    invocation_type VARCHAR(40) NOT NULL,
    provider VARCHAR(64),
    model VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    tool_calls_json TEXT,
    request_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    response_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    request_original_length INTEGER,
    response_original_length INTEGER,
    request_sha256 VARCHAR(64),
    response_sha256 VARCHAR(64),
    input_tokens INTEGER,
    output_tokens INTEGER,
    duration_ms INTEGER,
    error_type VARCHAR(255),
    error_message TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_llm_invocation_tenant_conversation
    ON llm_invocation (tenant_id, conversation_id, started_at);
CREATE INDEX idx_llm_invocation_tenant_trace
    ON llm_invocation (tenant_id, trace_id, sequence_no);
CREATE INDEX idx_llm_invocation_tenant_bot
    ON llm_invocation (tenant_id, bot_id, started_at);
CREATE INDEX idx_llm_invocation_tenant_status
    ON llm_invocation (tenant_id, status, started_at);
