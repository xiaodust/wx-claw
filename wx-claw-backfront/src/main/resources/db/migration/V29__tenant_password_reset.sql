-- 账号找回密码需要邮箱：注册时保存联系邮箱
ALTER TABLE tenant_account
    ADD COLUMN contact_email VARCHAR(128) NULL AFTER password_hash;

-- 密码重置令牌：只存 SHA-256 哈希，30 分钟过期，单次使用
CREATE TABLE tenant_password_reset (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id  VARCHAR(64) NOT NULL,
    account_id BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME    NOT NULL,
    used_at    DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    UNIQUE KEY uk_tenant_password_reset_token (token_hash),
    KEY idx_tenant_password_reset_expires (expires_at),
    KEY idx_tenant_password_reset_account (account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
