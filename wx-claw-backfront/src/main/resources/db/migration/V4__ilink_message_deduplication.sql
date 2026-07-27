CREATE TABLE ilink_message_receipt (
    tenant_id VARCHAR(64) NOT NULL,
    bot_id VARCHAR(128) NOT NULL,
    message_id INTEGER NOT NULL,
    from_user_id VARCHAR(255),
    create_time_ms INTEGER,
    received_at TIMESTAMP NOT NULL,
    PRIMARY KEY (tenant_id, bot_id, message_id)
);

CREATE INDEX idx_ilink_message_receipt_received_at
    ON ilink_message_receipt (received_at);
