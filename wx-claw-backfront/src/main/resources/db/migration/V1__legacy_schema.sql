CREATE TABLE IF NOT EXISTS ai_conversation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    created_time TIMESTAMP NOT NULL,
    last_message_time TIMESTAMP,
    message_count INTEGER NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    updated_time TIMESTAMP NOT NULL,
    username VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS ai_message (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    content TEXT,
    create_time TIMESTAMP NOT NULL,
    error_msg VARCHAR(1024),
    message_seq INTEGER NOT NULL,
    message_type INTEGER NOT NULL,
    reasoning_content TEXT,
    response_time INTEGER,
    session_id VARCHAR(255) NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS reminder_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP NOT NULL,
    cron_expression VARCHAR(100),
    executed_at TIMESTAMP,
    failure_reason VARCHAR(500),
    reminder_text VARCHAR(500) NOT NULL,
    retry_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    task_type VARCHAR(20) NOT NULL,
    trigger_time TIMESTAMP NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    action_params TEXT,
    action_type VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS user_learning (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP,
    instruction VARCHAR(500) NOT NULL,
    trigger VARCHAR(50) NOT NULL,
    user_id VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category VARCHAR(50) NOT NULL,
    created_at TIMESTAMP,
    key_name VARCHAR(100) NOT NULL,
    key_value VARCHAR(500) NOT NULL,
    source VARCHAR(20),
    updated_at TIMESTAMP,
    user_id VARCHAR(128) NOT NULL
);
