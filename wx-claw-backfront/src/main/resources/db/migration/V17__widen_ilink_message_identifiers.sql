ALTER TABLE ilink_message_receipt
    MODIFY COLUMN message_id BIGINT NOT NULL,
    MODIFY COLUMN create_time_ms BIGINT;
