CREATE TABLE tenant (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    plan_code VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO tenant (tenant_id, tenant_code, tenant_name, status, created_at, updated_at)
VALUES ('default', 'default', 'Default Tenant', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE tenant_bot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    bot_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    credential_ref VARCHAR(255),
    resume_context_path VARCHAR(512),
    UNIQUE (channel, bot_id)
);
CREATE INDEX idx_tenant_bot_tenant_status ON tenant_bot (tenant_id, status);

CREATE TABLE tenant_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    internal_user_id VARCHAR(128) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    bot_id VARCHAR(128) NOT NULL,
    channel_user_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    roles TEXT,
    UNIQUE (tenant_id, channel, bot_id, channel_user_id),
    UNIQUE (tenant_id, internal_user_id)
);

CREATE TABLE tenant_api_credential (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    credential_id VARCHAR(64) NOT NULL UNIQUE,
    secret_hash VARCHAR(512) NOT NULL,
    name VARCHAR(128) NOT NULL,
    scopes TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE ai_conversation RENAME TO ai_conversation_legacy;
CREATE TABLE ai_conversation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    internal_user_id VARCHAR(128) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    bot_id VARCHAR(128),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    message_count INTEGER NOT NULL,
    last_message_time TIMESTAMP,
    created_time TIMESTAMP NOT NULL,
    updated_time TIMESTAMP NOT NULL,
    UNIQUE (tenant_id, session_id)
);
INSERT INTO ai_conversation
SELECT id, 'default', session_id, username, COALESCE(NULLIF(username, ''), 'legacy'),
       'ILINK', 'default', is_active, message_count, last_message_time, created_time, updated_time
FROM ai_conversation_legacy;
DROP TABLE ai_conversation_legacy;
CREATE INDEX idx_ai_conversation_tenant_session ON ai_conversation (tenant_id, session_id);
CREATE INDEX idx_ai_conversation_tenant_user_active ON ai_conversation (tenant_id, internal_user_id, is_active);
CREATE INDEX idx_ai_conversation_tenant_bot ON ai_conversation (tenant_id, bot_id, created_time);

ALTER TABLE ai_message RENAME TO ai_message_legacy;
CREATE TABLE ai_message (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    message_type INTEGER NOT NULL,
    content TEXT,
    reasoning_content TEXT,
    message_seq INTEGER NOT NULL,
    response_time INTEGER,
    error_msg VARCHAR(1024),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    UNIQUE (tenant_id, conversation_id, message_seq),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id)
);
INSERT INTO ai_message
SELECT m.id, 'default', c.id, m.session_id, m.message_type, m.content, m.reasoning_content,
       m.message_seq, m.response_time, m.error_msg, m.create_time, m.update_time
FROM ai_message_legacy m
JOIN ai_conversation c ON c.tenant_id = 'default' AND c.session_id = m.session_id;
DROP TABLE ai_message_legacy;
CREATE INDEX idx_ai_message_tenant_session ON ai_message (tenant_id, session_id, message_seq);
CREATE INDEX idx_ai_message_tenant_conversation ON ai_message (tenant_id, conversation_id, message_seq);

ALTER TABLE reminder_task RENAME TO reminder_task_legacy;
CREATE TABLE reminder_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    internal_user_id VARCHAR(128) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    bot_id VARCHAR(128),
    channel_user_id VARCHAR(128),
    reminder_text VARCHAR(500) NOT NULL,
    action_type VARCHAR(50),
    action_params TEXT,
    trigger_time TIMESTAMP NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    cron_expression VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    failure_reason VARCHAR(500),
    retry_count INTEGER NOT NULL
);
INSERT INTO reminder_task
SELECT id, 'default', user_id, user_id, 'ILINK', 'default', user_id, reminder_text, action_type,
       action_params, trigger_time, task_type, cron_expression, status, created_at, executed_at,
       failure_reason, retry_count
FROM reminder_task_legacy;
DROP TABLE reminder_task_legacy;
CREATE INDEX idx_reminder_tenant_user_status ON reminder_task (tenant_id, user_id, status, trigger_time);

ALTER TABLE user_profile RENAME TO user_profile_legacy;
CREATE TABLE user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    category VARCHAR(50) NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    key_value VARCHAR(500) NOT NULL,
    source VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (tenant_id, user_id, category, key_name)
);
INSERT INTO user_profile
SELECT id, 'default', user_id, category, key_name, key_value, source, created_at, updated_at
FROM user_profile_legacy;
DROP TABLE user_profile_legacy;

ALTER TABLE user_learning RENAME TO user_learning_legacy;
CREATE TABLE user_learning (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    trigger_pattern VARCHAR(50) NOT NULL,
    instruction VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP
);
INSERT INTO user_learning
SELECT id, 'default', user_id, trigger_pattern, instruction, active, created_at
FROM user_learning_legacy;
DROP TABLE user_learning_legacy;
CREATE INDEX idx_user_learning_tenant_user_active ON user_learning (tenant_id, user_id, active);
