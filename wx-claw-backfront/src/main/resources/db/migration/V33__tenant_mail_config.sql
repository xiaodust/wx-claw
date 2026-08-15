CREATE TABLE tenant_mail_config (
    tenant_id      VARCHAR(64)  NOT NULL,
    smtp_host      VARCHAR(255) NOT NULL,
    smtp_port      INT          NOT NULL,
    username       VARCHAR(255) NOT NULL,
    password_cipher TEXT        NOT NULL,
    from_address   VARCHAR(255) NOT NULL,
    enabled        TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at     DATETIME(6),
    PRIMARY KEY (tenant_id)
);
