ALTER TABLE ilink_message_receipt
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DONE' AFTER received_at,
    ADD COLUMN processed_at TIMESTAMP NULL AFTER status;

CREATE INDEX idx_ilink_message_receipt_status_received
    ON ilink_message_receipt (status, received_at);
