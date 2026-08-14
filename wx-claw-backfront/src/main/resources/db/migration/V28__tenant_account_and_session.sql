-- 控制台账号：注册时创建，用于账号密码登录（密码只存 PBKDF2 哈希）
CREATE TABLE tenant_account (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_tenant_account_username (username),
    KEY idx_tenant_account_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 登录会话：服务端保存 token 的 SHA-256 哈希，支持过期与清理
CREATE TABLE tenant_session (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL,
    account_id   BIGINT      NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    expires_at   DATETIME    NOT NULL,
    created_at   DATETIME    NOT NULL,
    last_used_at DATETIME    NULL,
    UNIQUE KEY uk_tenant_session_token_hash (token_hash),
    KEY idx_tenant_session_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
