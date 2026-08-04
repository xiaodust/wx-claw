CREATE TABLE IF NOT EXISTS ilink_resume_context (
    tenant_id VARCHAR(64) NOT NULL,
    bot_id VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, bot_id)
);

ALTER TABLE reminder_task
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
