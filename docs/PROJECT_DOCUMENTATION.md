# WX-Claw 项目开发与维护文档

## 1. 项目概述

WX-Claw 是一个基于微信 ILink 的多租户智能体平台，提供：

- 管理端 Web 控制台
- 用户端 Web 控制台
- 微信 Bot 接入与消息处理
- 多租户数据隔离
- LLM 任务规划与工具调用
- 图片、视频、语音生成
- 知识库检索
- 定时提醒、天气、搜索、邮件等工具

项目当前采用单后端、单 MySQL、双前端容器部署，适合中小规模团队和个人使用。

## 2. 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1.0 |
| ORM / 迁移 | Spring Data JPA、Flyway |
| 数据库 | MySQL 8 |
| AI | Spring AI 2.0.0、OpenAI 兼容接口 |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router |
| 部署 | Docker、Docker Compose、Nginx |
| 测试 | JUnit 5、Mockito、Testcontainers |

## 3. 系统架构

```text
Browser
  │
  ├─ admin.example.com ─→ Nginx(admin) ─→ /api ─→ backend:8080
  └─ app.example.com  ─→ Nginx(user)  ─→ /api ─→ backend:8080

backend
  ├─ MySQL 8
  ├─ 微信 ILink SDK
  ├─ LLM Providers
  ├─ SMTP
  ├─ RAGFlow（可选）
  └─ JobHelper（可选）
```

前端与后端同源部署时，前端通过相对路径 `/api` 访问后端，由 Nginx 反代。

## 4. 核心模块

### 4.1 多租户

核心类：

- `Tenant`
- `TenantAccount`
- `TenantSession`
- `TenantApiCredential`
- `TenantContext`
- `TenantContextHolder`
- `TenantOwnedEntity`
- `TenantOwnedEntityListener`

规则：

- 用户请求进入时，由 `ApiKeyAuthFilter` 认证并写入租户上下文。
- Service 通过 `TenantContextHolder.require()` 获取当前租户。
- 新增租户私有实体时，`TenantOwnedEntityListener` 防止跨租户写入。
- 管理端查询通过 `AdminAccessGuard.resolveTenant()` 统一鉴权。

### 4.2 认证与会话

- 管理端管理员账号密码登录。
- 租户账号密码登录。
- API Key 认证。
- Session Token 只存 SHA-256。
- 浏览器控制台使用 HttpOnly Cookie，Cookie 名为 `WXCLAW_SESSION`。
- CSRF 通过 SameSite 和 Origin/Referer 校验。

### 4.3 AI Agent 规划

入口：

```text
AgentResponseProcessor
  → AgentOrchestrator
  → PlainTextLlmService
  → PlanValidator
  → TaskExecutor
  → ToolHandler
```

规划模型输出 JSON，格式为：

```json
{
  "steps": [
    {
      "step": 1,
      "tool": "chat",
      "params": {},
      "description": "处理用户请求"
    }
  ]
}
```

已支持：

- `chat`
- `voice_synthesize`
- `image_generate`
- `video_generate`
- `career_resume_score`
- `career_resume_analyze`
- `career_job_recommendation`
- `career_job_search`
- `career_resume_retrieve`
- `career_resume_clear`
- `knowledge_file_retrieve`

### 4.4 工具体系

底层工具由 `LlmToolRegistry` 注册，用于 chat 模型的 function calling：

- Time
- Weather
- Web Search
- Reminder
- Memory
- Mail
- Summary

高层工具由 `ToolRegistry` 注册，由 `AgentOrchestrator` 编排：

- Chat
- Image Generate
- Video Generate
- Voice Synthesize
- Knowledge File Retrieve

### 4.5 邮件体系

现在拆分为：

1. 系统邮件发送器
   - 用于登录验证码、密码重置。
   - 配置来源：后端 `.env`。

2. 租户邮件发送器
   - 用于 AI 主动发邮件。
   - 配置来源：前端用户设置页。
   - 数据表：`tenant_mail_config`。
   - SMTP 密码使用 AES-GCM 加密存储。

## 5. 项目目录结构

```text
wx-claw/
├─ wx-claw-backfront/
│  ├─ src/main/java/com/dust/wxclawbackfront/
│  │  ├─ admin/
│  │  ├─ bot/
│  │  ├─ config/
│  │  ├─ ilink/
│  │  ├─ observability/
│  │  ├─ tenancy/
│  │  └─ user/
│  ├─ src/main/resources/
│  │  ├─ ai/prompts/
│  │  ├─ ai/skills/
│  │  ├─ db/migration/
│  │  ├─ application.example.yml
│  │  └─ logback-spring.xml
│  └─ src/test/
├─ wx-claw-admin/
│  ├─ src/
│  ├─ Dockerfile
│  ├─ nginx.conf
│  └─ package.json
├─ wx-claw-user/
│  ├─ src/
│  ├─ Dockerfile
│  ├─ nginx.conf
│  └─ package.json
├─ docker-compose.yml
├─ .env.example
└─ docs/
```

## 6. 数据库与迁移

数据库迁移使用 Flyway，文件位于：

```text
wx-claw-backfront/src/main/resources/db/migration/
```

关键表：

| 表 | 说明 |
|---|---|
| `tenant` | 租户 |
| `tenant_account` | 租户控制台账号 |
| `tenant_session` | 租户会话 |
| `tenant_api_credential` | 租户 API Key |
| `tenant_invite_code` | 邀请码 |
| `tenant_email_verification` | 邮箱验证码 |
| `tenant_ai_config` | 租户 AI Key 与模型配置 |
| `tenant_mail_config` | 租户发件邮箱配置 |
| `admin_account` | 管理端账号 |
| `admin_session` | 管理端会话 |
| `ai_conversation` | AI 会话 |
| `ai_message` | AI 消息 |
| `llm_invocation` | LLM 调用审计 |

新增数据库表时必须新增 Flyway 迁移，禁止直接修改生产库。

## 7. 环境变量

完整模板见：

```text
.env.example
```

关键变量：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `DB_USERNAME` | 数据库用户 |
| `DB_PASSWORD` | 数据库密码 |
| `AI_KEY_ENCRYPTION_KEY` | 租户 AI Key 加密密钥 |
| `API_BOOTSTRAP_KEY` | 首次启动 API Key |
| `ADMIN_PASSWORD` | 管理端初始密码 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端域名 |
| `PASSWORD_RESET_BASE_URL` | 密码重置链接前缀 |
| `COOKIE_SECURE` | 是否 HTTPS Cookie |
| `TRUST_FORWARDED_HEADERS` | 是否信任代理头 |
| `MAIL_HOST` | 系统邮件 SMTP |
| `MAIL_PORT` | 系统邮件端口 |
| `MAIL_USERNAME` | 系统邮件账号 |
| `MAIL_PASSWORD` | 系统邮件授权码 |
| `SENIVERSE_KEY` | 天气 API Key |
| `JOB_HELPER_ENABLED` | 是否启用 JobHelper |
| `JOB_HELPER_MCP_ENABLED` | 是否启用 JobHelper MCP |

## 8. 本地开发

### 8.1 Docker 方式

```powershell
copy .env.example .env
docker compose up -d --build
```

访问：

- 管理端：http://localhost:3000
- 用户端：http://localhost:3001

### 8.2 原生方式

后端：

```powershell
cd wx-claw-backfront
.\mvnw.cmd spring-boot:run
```

用户端：

```powershell
cd wx-claw-user
npm install
npm run dev
```

管理端：

```powershell
cd wx-claw-admin
npm install
npm run dev
```

## 9. Docker 部署

推荐结构：

```text
公网
  ↓
Nginx / Caddy :443
  ↓
admin:3000
user:3001
backend:8080（内部）
mysql:3306（内部）
```

常用命令：

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose down
docker compose down -v
```

## 10. API 概览

公开接口：

```text
POST /api/public/auth/email-code
POST /api/public/auth/login
POST /api/public/auth/admin-login
POST /api/public/auth/logout
POST /api/public/auth/forgot-password
POST /api/public/auth/reset-password
POST /api/public/tenants/register
```

用户接口：

```text
GET/POST/DELETE /api/user/bots
GET /api/user/bots/{botId}/qr
GET /api/user/bots/{botId}/conversations
GET /api/user/ai-config
PUT /api/user/ai-config/{capability}
PUT /api/user/ai-config/{capability}/model
GET /api/user/mail-config
PUT /api/user/mail-config
DELETE /api/user/mail-config
GET /api/user/account
POST /api/user/account/setup
POST /api/user/account/password
```

管理端接口：

```text
GET /api/admin/overview
GET /api/admin/bots
GET /api/admin/conversations
GET /api/admin/conversations/{id}/messages
GET /api/admin/conversations/{id}/invocations
GET /api/admin/invite-codes
POST /api/admin/invite-codes
DELETE /api/admin/invite-codes/{code}
POST /api/admin/account/password
```

## 11. 安全设计

当前已实现：

- PBKDF2 密码与 API Key 哈希
- Session Token 只存 SHA-256
- HttpOnly Cookie
- SameSite / Origin CSRF 校验
- 多租户上下文隔离
- 租户实体写入监听
- 安全响应头
- 请求体大小限制
- 自定义 LLM baseUrl SSRF 校验
- `knowledge_upload` URL 下载限制
- AI Key 字段级 AES-GCM 加密
- 密码复杂度策略
- CORS 白名单
- 登录与注册限流
- 依赖审计

## 12. 常见问题

### 12.1 Agent 规划返回“缺少 steps”

可能原因：

- 模型返回单个 step 对象，而不是 `steps` 数组。
- 模型返回数组时解析异常。
- 规划 token 不足。

已增加：

- JSON 输出模式
- 数组/单对象兼容解析
- `plan.max-tokens` 增大
- 原始规划响应日志

### 12.2 cpolar 公网 404

原因通常是 cpolar 免费域名在重启后变化。

处理：

1. 重新运行 cpolar。
2. 读取新的公网地址。
3. 更新 `.env`。
4. 执行 `docker compose up -d`。

### 12.3 后端日志出现 `No EntityManager with actual transaction`

检查定时清理方法是否缺少 `@Transactional`。

### 12.4 邮箱发送失败

检查：

- 系统邮箱是否配置。
- 用户邮箱是否在前端设置页配置。
- 授权码是否正确。
- 白名单是否包含收件人。

## 13. 维护与扩展建议

### 新增数据库表

1. 编写 Flyway 迁移。
2. 新增实体和 Repository。
3. 更新文档。

### 新增 Agent 工具

1. 实现 `ToolHandler`。
2. 在规划提示词中声明工具。
3. 更新 `PlanValidator` 白名单。
4. 补充测试。

### 新增聊天工具

1. 实现 `AiToolProvider`。
2. 设置 `order`。
3. 更新 `LlmToolRegistry` 自动注册。

## 14. 测试与验证

后端：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=!WxClawBackfrontApplicationTests" test
```

前端：

```powershell
npm run build
npm audit
```

安全依赖扫描：

```powershell
.\mvnw.cmd -DskipTests org.owasp:dependency-check-maven:check
```

## 15. 待办事项

- 定时邮件任务切换到租户邮箱配置。
- 多实例限流迁移到 Redis。
- 邀请码哈希化。
- 更细粒度的审计日志。
- 多实例部署时 Flyway 单独执行。
