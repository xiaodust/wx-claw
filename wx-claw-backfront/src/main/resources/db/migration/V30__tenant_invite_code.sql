-- 注册邀请码：平台级资源（不属于某个租户），注册时原子扣减
CREATE TABLE tenant_invite_code (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(32)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    quota      INT          NULL COMMENT '可注册次数，NULL 表示不限',
    used_count INT          NOT NULL DEFAULT 0,
    expires_at DATETIME     NULL COMMENT 'NULL 表示永不过期',
    remark     VARCHAR(200) NULL,
    created_by VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL,
    UNIQUE KEY uk_tenant_invite_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
