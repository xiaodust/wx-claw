-- 邮箱验证码：注册时校验邮箱归属，密码找回沿用同一邮箱路径
CREATE TABLE tenant_email_verification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(128) NOT NULL,
    purpose    VARCHAR(20)  NOT NULL COMMENT 'REGISTER / RESET',
    code_hash  CHAR(64)     NOT NULL,
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL,
    KEY idx_tenant_email_verification_lookup (email, purpose, used_at),
    KEY idx_tenant_email_verification_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
