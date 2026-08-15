-- 平台管理员账号：独立于租户账号，密码只存 PBKDF2 哈希
CREATE TABLE admin_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'SUPER_ADMIN',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_admin_account_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 管理端登录会话：token 只存 SHA-256，默认 7 天过期
CREATE TABLE admin_session (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_account_id BIGINT   NOT NULL,
    token_hash       CHAR(64) NOT NULL,
    expires_at       DATETIME NOT NULL,
    created_at       DATETIME NOT NULL,
    last_used_at     DATETIME NULL,
    UNIQUE KEY uk_admin_session_token_hash (token_hash),
    KEY idx_admin_session_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
