> 安装、Docker 部署、环境变量与配置、FAQ（部署/运维向）。
# 快速开始

## 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8+（Flyway 自动迁移建表）
- Docker（用于运行 RAGFlow 知识库服务，可选）
- 微信 ILink SDK 账号
- 至少 4GB 可用内存（推荐 8GB+）

## 1. 启动 RAGFlow 知识库服务（可选）

如果需要使用知识库功能，需要先启动 RAGFlow 服务：

```bash
# 克隆 RAGFlow 仓库
git clone https://github.com/infiniflow/ragflow.git
cd ragflow

# 使用 Docker Compose 启动
docker compose -f docker-compose.yml up -d

# 等待服务启动完成（首次启动需要下载镜像，耗时较长）
# 访问 http://localhost 验证服务是否启动成功
# 默认端口：80（Web UI）、9380（API）
```

**RAGFlow 环境变量配置：**

启动后需要在 RAGFlow Web UI 中：

1. 注册账号并登录
2. 创建知识库（Dataset），获取 Dataset ID
3. 创建聊天助手（Chat），获取 Chat ID
4. 在 API 管理中生成 API Key

## 2. 克隆项目

```bash
git clone https://github.com/xiaodust/wx-claw.git

cd wx-claw
```

## 3. 配置文件

```bash
cp wx-claw-backfront/src/main/resources/application.example.yml \
   wx-claw-backfront/src/main/resources/application.yml
```

## 4. 填写配置

编辑 `application.yml`，配置数据库与 AI 服务密钥：

```yaml
DB_URL: jdbc:mysql://127.0.0.1:3306/wx_claw_bot?...   # MySQL 连接
DB_USERNAME: wxclaw
DB_PASSWORD: your-password

spring:
  ai:
    openai:
      api-key: your-api-key        # 对话模型 API 密钥（可按租户覆盖）

wxclaw:
  api:
    bootstrap-key: your-bootstrap-key   # 首个管理凭据（* 权限）
  ragflow:
    enabled: true                   # 启用知识库功能
    base-url: http://localhost:9380 # RAGFlow API 地址
    api-key: your-ragflow-key       # RAGFlow API 密钥
    dataset-id: your-dataset-id     # 知识库 ID
    chat-id: your-chat-id           # 聊天助手 ID
```

## 5. 运行后端

```bash
cd wx-claw-backfront
mvn spring-boot:run
```

后端启动时 Flyway 自动执行数据库迁移（`db/migration/V*.sql`），监听 `http://localhost:8080`。

## 6. 运行用户端（官网 + 控制台）

```bash
cd wx-claw-user
npm install
npm run dev
```

浏览器访问 `http://localhost:3001`：`/` 为官网主页，注册后进入用户控制台。

## 7. 运行管理端

管理端用于查看 Bot 实时状态、对话历史和模型原始调用记录：

```bash
cd wx-claw-admin
npm install
npm run dev
```

浏览器访问 `http://localhost:3000`，支持两种登录方式：

- **管理员账号**：用户名密码登录（默认用户名 `admin`，密码由 `ADMIN_PASSWORD` 提供；未配置时首次启动自动生成并打印到日志）
- **API Key**：`<credentialId>.<secret>` 格式的管理 Key（Bootstrap 凭据 `*` scope 可访问管理端）

## 8. 初始化注册链路

1. 管理端（3000）→「注册邀请码」→ 生成邀请码（可设配额/有效期）
2. 用户端（3001）→ 注册页 → 填写邀请码、邮箱并接收验证码 → 设置用户名密码
3. 使用用户名密码登录控制台，创建 Bot 扫码连接微信

> 老租户（如 `default`，仅有 API Key 无账号）可在登录页「使用 API Key 激活账号」一次性完善用户名/邮箱/密码。

生产构建：

```bash
npm run build
```

模型调用审计默认最多保存每个请求或响应 2 MB，密钥、Token 和 Base64 媒体会在持久化前自动脱敏。



## Docker 部署

## 本地 Docker 运行

```powershell
copy .env.example .env
docker compose up -d --build
```

访问：

- 管理端：http://localhost:3000
- 用户端：http://localhost:3001

后端和 MySQL 不直接暴露公网，只通过前端 Nginx 反代 `/api`。

## 关键环境变量

```env
MYSQL_ROOT_PASSWORD=强密码
DB_USERNAME=wxclaw
DB_PASSWORD=强密码
AI_KEY_ENCRYPTION_KEY=长随机值
API_BOOTSTRAP_KEY=长随机值
ADMIN_PASSWORD=强密码
CORS_ALLOWED_ORIGINS=https://admin.example.com,https://app.example.com
PASSWORD_RESET_BASE_URL=https://app.example.com
COOKIE_SECURE=true
TRUST_FORWARDED_HEADERS=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=no-reply@example.com
MAIL_PASSWORD=邮箱授权码
SENIVERSE_KEY=
JOB_HELPER_ENABLED=false
JOB_HELPER_MCP_ENABLED=false
```

生产环境必须设置 `COOKIE_SECURE=true` 和 `TRUST_FORWARDED_HEADERS=true`。

## 使用 cpolar 临时公网访问

```powershell
# 任意内网穿透工具均可（cpolar / ngrok / cloudflared 等）
cpolar authtoken <你的token>          # 仅首次使用 cpolar 时需要
cpolar http 3000 -inspect-addr 127.0.0.1:4042 -log stdout
cpolar http 3001 -inspect-addr 127.0.0.1:4043 -log stdout
```

将 cpolar 返回的 HTTPS 地址填入 `.env`：

```env
CORS_ALLOWED_ORIGINS=https://管理端公网地址,https://用户端公网地址
PASSWORD_RESET_BASE_URL=https://用户端公网地址
```

然后重启：

```powershell
docker compose up -d
```

## 用户发件邮箱配置

AI 通过 `send_email` 工具发邮件时，使用当前租户在前端设置页配置的 SMTP 邮箱。
后端 `.env` 中的系统邮箱仅用于登录验证码和密码重置。


## 配置说明

## 环境变量

> 后端不再内置默认 AI Key：各能力（对话/图片/视频/语音/搜索）的 API Key 由租户在
> 用户控制台「API Key 与模型设置」页自行配置；下面的环境变量仅为可选兜底。

| 变量名                   | 说明                 |
| --------------------- | ------------------ |
| `DB_URL`              | MySQL 连接地址（默认本机 3306） |
| `DB_USERNAME`         | MySQL 用户名（默认 wxclaw） |
| `DB_PASSWORD`         | MySQL 密码 |
| `AI_API_KEY`          | AI 模型 API 密钥       |
| `AI_BASE_URL`         | AI 模型 API 地址       |
| `API_CREDENTIAL_ID`   | Bootstrap 凭据 ID（默认 default） |
| `API_BOOTSTRAP_KEY`   | Bootstrap API Key（首个管理凭据） |
| `ADMIN_USERNAME`      | 管理端初始账号用户名（默认 admin） |
| `ADMIN_PASSWORD`      | 管理端初始账号密码（不配置则启动时自动生成） |
| `REGISTRATION_REQUIRE_INVITE` | 注册是否需要邀请码（默认 true） |
| `REGISTRATION_INVITE_CODES`   | 启动时预置的邀请码（逗号分隔） |
| `PASSWORD_RESET_BASE_URL`     | 密码重置邮件中的前端地址（默认 http://localhost:3001） |
| `SILICONFLOW_API_KEY` | SiliconFlow 图片生成密钥 |
| `TTS_API_KEY`         | TTS 语音合成密钥         |
| `BOCHA_API_KEY`       | 博查搜索密钥             |
| `SENIVERSE_KEY`       | 心知天气密钥             |
| `MAIL_USERNAME`       | 邮箱账号               |
| `MAIL_PASSWORD`       | 邮箱授权码              |
| `CORS_ALLOWED_ORIGINS`| 允许的跨域来源（默认 3000/3001） |
| `RAGFLOW_API_KEY`     | RAGFlow 知识库密钥      |
| `RAGFLOW_BASE_URL`    | RAGFlow 服务地址       |

## 性能配置

```yaml
wxclaw:
  ai:
    chat:
      max-tokens: 768      # 最大输出 token
      timeout: PT25S       # 超时时间
    context:
      max-chars: 7000      # 上下文最大字符数
    document:
      enabled: true        # 启用文档发送功能
      threshold: 1000      # 触发文档发送的字符阈值
    thinking:
      type: disabled       # 模型思考模式

  # 安全配置
  api:
    auth-enabled: true     # 启用 API 认证
    key: ${API_KEY:your-secret-api-key-here}  # API 密钥
    bootstrap-key: ${API_BOOTSTRAP_KEY:your-bootstrap-key}  # 管理凭据
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3001}  # 管理端 + 用户端
    # 注册邀请码
    registration:
      require-invite: ${REGISTRATION_REQUIRE_INVITE:true}
      invite-codes: ${REGISTRATION_INVITE_CODES:}
      max-per-ip: 5
      max-per-email: 3
    # 登录限流与会话
    login:
      max-per-user-and-ip: 10
      max-per-ip: 30
    session:
      ttl: PT168H           # 会话有效期（默认 7 天）
    # 邮箱验证码（注册）
    email-code:
      ttl: PT10M
      max-per-email-and-ip: 5
    # 密码重置
    password-reset:
      base-url: ${PASSWORD_RESET_BASE_URL:http://localhost:3001}
      token-ttl: PT30M

  # 用户验证配置
  user:
    verification:
      enabled: false       # 启用用户验证白名单
      allowed-user-ids:    # 允许的用户 ID 列表
        - "wxid_example1"
        - "wxid_example2"

  # 线程池配置
  thread-pool:
    message-processing:    # 消息处理线程池
      core-size: 4
      max-size: 8
      queue-capacity: 100
    async-save:            # 异步保存线程池
      core-size: 2
      max-size: 4
      queue-capacity: 100
    prompt-executor:       # 提示词处理线程池
      core-size: 2
      max-size: 4
      queue-capacity: 50
    video-executor:        # 视频处理线程池
      core-size: 1
      max-size: 2
      queue-capacity: 10

  # ILink 重连配置
  ilink:
    reconnect:
      max-attempts: 5      # 连续失败达到上限后清除旧会话并重新扫码
      delay-seconds: 30    # 重连间隔（秒）

  # Agent 规划配置
  agent:
    plan:
      max-retries: 3       # 规划最大重试次数

  # 文件上传配置
  upload:
    max-file-size-mb: 50   # 最大文件上传大小（MB）
```

## 知识库配置

```yaml
wxclaw:
  ragflow:
    enabled: true                           # 启用知识库功能
    base-url: http://localhost:9380         # RAGFlow 服务地址
    api-key: your-ragflow-api-key          # RAGFlow API 密钥
    dataset-id: your-dataset-id            # 知识库 ID
    chat-id: your-chat-id                  # 聊天助手 ID（用于问答）
    timeout: PT30S                         # 请求超时时间
```


## 常见问题

## Q: 如何更换 AI 模型？

登录用户控制台 →「API Key 与模型设置」，按能力（对话/图片/视频）选择服务商、模型并配置自己的 API Key；也支持直接输入自定义模型名。

后端默认配置（租户未覆盖时生效）修改 `application.yml`：

```yaml
spring:
  ai:
    openai:
      base-url: https://your-api-url
      api-key: your-key
      chat:
        model: your-model-name
```

## Q: 如何关闭某个功能？

在 `application.yml` 中设置对应的 `enabled: false`：

```yaml
wxclaw:
  reminder:
    enabled: false
  mail:
    enabled: false
```

## Q: 如何查看日志？

修改日志级别：

```yaml
logging:
  level:
    com.dust.wxclawbackfront: debug
```


