# WX-Claw 微信智能体平台

一个多租户的微信 ILink 智能体平台：基于 Spring AI 的 Agent 编排系统实现多工具协同调度与多模态回复，并配套官网、用户控制台与管理端三个 Web 端。

> [!IMPORTANT]
> **合规与免责声明**：本项目通过第三方微信接入通道（ILink）实现智能体消息收发，与腾讯公司及微信官方产品无任何关联，亦非官方 SDK。项目仅供技术学习与研究使用；使用者须自行评估并遵守微信平台相关服务条款、当地法律法规及所对接账号的服务协议。因使用本项目导致的账号限制、服务中断或其他风险，由使用者自行承担。

## 版本信息

当前版本：**v3.0**

### v3.0 主要更新（平台化）

- **官网主页 + 租户自助注册** - 介绍页 + 注册页，注册从"后端配置"变为自助流程
- **邀请码注册制** - 注册需要邀请码（内测阶段保留），管理端可生成/停用邀请码，杜绝无限注册
- **邮箱必填 + 验证码校验** - 注册时向邮箱发送 6 位验证码，验证通过才创建租户
- **账号密码登录** - 控制台改用用户名 + 密码登录，会话 token 7 天有效；API Key 仅用于接口调用与一次性激活账号
- **密码找回/重置/修改** - 忘记密码走邮箱重置链接（30 分钟单次有效）；登录后支持修改密码并吊销全部会话
- **用户控制台** - 自助创建 Bot、扫码连接、查看聊天记录、配置各能力 API Key 与模型
- **管理端** - 运行总览、Bot 状态、对话与调用审计、注册邀请码管理
- **多服务商模型** - 对话/图片/视频支持 DeepSeek、火山方舟、OpenAI、SiliconFlow、阿里云 DashScope 自由切换
- **多租户隔离与安全加固** - 租户级数据隔离、PBKDF2 密码哈希、登录/注册/验证码限流、防用户枚举
- **工业暗色主题 UI** - 官网、注册、登录、控制台统一视觉风格

## 功能特性

### 平台能力

- **官网主页** - 产品介绍、功能展示、使用流程与 FAQ，注册入口直达
- **租户自助注册** - 邀请码 + 邮箱验证码双重校验，注册成功即签发会话
- **账号体系** - 用户名密码登录、忘记密码（邮箱重置）、登录后修改密码、无账号租户一次性激活
- **用户控制台** - 创建/删除 Bot、扫码连接微信、实时状态、聊天记录、AI 能力与模型配置
- **管理端** - Bot 运行状态、对话与 LLM 调用审计、注册邀请码管理
- **多租户隔离** - 所有业务数据按租户隔离，API Key 按 Scope 收敛权限

### 核心功能

- **Agent 编排系统** - LLM 驱动的高层任务规划，提示词模板化，自动拆解复杂请求为多步骤执行
- **Spring AI Function Calling** - chat 模型自主决定调用哪些工具，工具是"大模型的手"
- **智能对话** - 基于大模型的自然语言对话
- **上下文记忆** - 支持多轮对话，记住上下文
- **用户画像** - 记住用户偏好和习惯，提供个性化服务
- **知识库管理** - 集成 RAGFlow 知识库，支持文档检索和问答
- **职业助手** - 简历存取与岗位搜索/推荐，支持本地文档与 JobHelper MCP 联动
- **多媒体回复** - 单次任务可同时返回文本、图片、语音、视频等多种附件
- **智能文档发送** - 长文本自动转换为文件发送，优化阅读体验
- **消息防抖** - 防止短时间内重复调用 AI
- **安全认证** - API 密钥认证，限制 CORS 来源，保护接口安全
- **用户验证** - 白名单机制，控制访问权限
- **异步处理** - 消息异步处理，提高系统响应性
- **自动重连** - ILink 连接断开后自动重连，保障服务稳定
- **上下文传播** - 确保异步操作中用户上下文的一致性
- **异常处理** - 完善的异常处理和用户友好的错误反馈

### 工具能力

底层工具通过 Spring AI function calling 注册，由 chat 模型自主决定何时调用：

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 时间查询        | 获取当前时间、日期、星期                 |
| 天气查询        | 查询任意城市实时天气和预报                |
| 网络搜索        | 搜索最新资讯、新闻、百科                 |
| 提醒设置        | 一次性或周期性提醒（每天/每周/每月）          |
| 对话总结        | 生成日报、周报、月报                   |
| 邮件发送        | 发送邮件通知                       |
| 知识库检索      | 从知识库中搜索相关文档片段                |
| 知识库问答      | 向知识库提问并获取智能回答                |
| 知识库上传      | 上传文件到知识库（PDF、DOCX、TXT等）      |
| 知识库文档管理    | 列举、删除、更新知识库中的文档              |
| 引用消息处理      | 处理和回应用户引用的先前消息                |

### 高层编排能力

Agent 编排器只规划高层任务，底层工具调用由 chat 模型自行处理：

| 功能              | 说明                                        |
| ---------------- | ----------------------------------------- |
| 智能对话           | 普通对话，支持调用底层工具（chat）                    |
| 图片生成           | 根据描述生成图片（SiliconFlow / 火山方舟 / OpenAI） |
| 语音回复           | TTS 语音合成，含文本口语化润色与超时重试                 |
| 视频生成           | 根据描述生成视频                                |
| 简历管理           | 保存/取回/清除简历（PDF，JobHelper MCP 持久化）      |
| 简历分析/评分       | 基于已保存简历做分析与评分                          |
| 岗位搜索           | 按城市/关键词搜索实习、校招、社招岗位                   |
| 岗位推荐           | 结合简历推荐匹配岗位                              |
| 知识文件取回         | 从知识库取回用户存储的文件并发送给用户                    |

> 说明：编排模型负责意图判断与任务拆解（读取 `ai/prompts/agent-planner.md` 提示词），各步骤由对应 `ToolHandler` 执行；底层工具的自主调用仍由 chat 模型通过 function calling 完成。

### 快捷命令

| 命令                 | 说明     |
| ------------------ | ------ |
| `#help` / `#帮助`    | 显示帮助信息 |
| `#tools` / `#工具`   | 查看功能列表 |
| `#version` / `#版本` | 显示版本信息 |

### 知识库功能

集成 RAGFlow 知识库，支持以下操作：

- **知识库检索** - 从知识库中搜索相关文档片段
- **知识库问答** - 向知识库提问并获取智能回答
- **文件上传** - 通过微信直接发送文件，自动上传到知识库
- **文档管理** - 列举、删除、更新知识库中的文档

支持的文件类型：PDF、DOCX、TXT、MD、CSV、XLSX 等

**使用示例：**

```
用户：帮我查一下产品安装文档
AI：[调用 knowledge_search 或 knowledge_ask] 根据知识库内容回答...

用户：[发送文件] 技术文档.pdf
AI：收到文件：技术文档.pdf，已成功上传到知识库。

用户：知识库里有哪些文档？
AI：[调用 knowledge_list_documents] 知识库中共有 5 个文档...

用户：删除文档 xxx
AI：[调用 knowledge_delete_document] 文档删除成功。
```

### 职业助手（简历与岗位）

对接 JobHelper MCP 服务，提供简历全生命周期与岗位能力：

- **简历保存** - 用户发送 PDF 简历后自动保存，原始文件持久化在 JobHelper 服务端，本地仅保留短期上下文缓存（过期自动清理，可回源加载）
- **简历管理** - 取回 / 清除已保存的简历
- **简历分析 / 评分** - 基于已保存简历生成分析与评分
- **岗位搜索** - 按城市、关键词搜索实习 / 校招 / 社招岗位
- **岗位推荐** - 结合简历自动推荐匹配岗位，并支持多城市、多关键词的复合请求

注意事项：

- 简历格式仅支持 PDF，大小不超过 10MB
- 未保存简历时调用分析、评分、推荐会返回友好提示，不会报错
- 岗位搜索时如缺少城市或关键词，工具会主动追问补齐

**使用示例：**

```
用户：[发送文件] 我的简历.pdf
AI：收到简历，已保存。之后可以直接让我分析简历或推荐岗位。

用户：根据我的简历给我推荐一下实习岗位
AI：[调用 career_job_recommendation] 根据你的简历推荐如下岗位...

用户：帮我搜索上海后端实习岗位
AI：[调用 career_job_search] 为你找到以下上海后端实习岗位...

用户：把我的简历发给我
AI：[调用 career_resume_retrieve] 附件：我的简历.pdf

用户：分析一下我的简历
AI：[调用 career_resume_analyze] 简历分析结果...
```

### 智能文档发送

当 AI 回复内容较长时（超过 1000 字符），系统会自动：

1. 将长文本转换为文档文件（支持 TXT 或 Markdown 格式）
2. 以文件形式发送给用户
3. 优化阅读体验，避免消息过长

触发条件：

- 回复内容超过 1000 字符
- 用户请求生成文档（如"帮我写个报告"、"生成 markdown 文档"）

**使用示例：**

```
用户：帮我写一份详细的项目实施方案
AI：[生成长文本] → 自动转换为文件发送
    已生成文档，请查收。[附件：项目实施方案.txt]

用户：用 markdown 格式写个技术文档
AI：[生成 Markdown] → 自动转换为 .md 文件发送
    已生成文档，请查收。[附件：技术文档.md]
```

## 技术架构

```
平台层
  ├─ 官网 + 用户控制台（Vue 3，端口 3001）
  ├─ 管理端（Vue 3，端口 3000）
  └─ REST API（后端 8080）
       ├─ /api/public/*    注册 / 登录 / 找回 / 邮箱验证码
       ├─ /api/user/*      用户控制台（Bot / 会话 / AI 配置 / 账号）
       └─ /api/admin/*     管理端（Bot 状态 / 对话审计 / 邀请码）

租户与账号层
  ├─ tenant                    租户主数据（tenantId / tenantCode）
  ├─ tenant_account            控制台账号（用户名 / PBKDF2 密码哈希 / 邮箱）
  ├─ tenant_session            登录会话（token 仅存 SHA-256，7 天过期）
  ├─ tenant_api_credential     接口 API Key（PBKDF2，Scope 收敛）
  ├─ tenant_invite_code        注册邀请码（配额 / 有效期 / 原子扣减）
  └─ tenant_email_verification 邮箱验证码（6 位，10 分钟单次有效）

接入层
  └─ 微信 ILink SDK

消息分发层
  └─ ILinkMessageDispatcher
       ├─ 消息解析（文本 / 图片 / 语音 / 文件 / 引用消息）
       ├─ 消息防抖（3 秒内相同消息去重）
       ├─ 引用消息处理（处理用户引用的先前消息）
       └─ 文件处理（下载文件 → 上传知识库）

Agent 编排层
  └─ AgentOrchestrator
       ├─ PromptLoader（读取 ai/prompts/agent-planner.md 规划提示词模板）
       ├─ LLM 任务规划（所有消息统一交规划模型拆解：chat / voice / image / video / career / file）
       ├─ PlanValidator（校验并重试非法规划结果）
       ├─ TaskExecutor（按步骤执行，合并多步媒体附件）
       └─ ToolRegistry（自动发现 ToolHandler 实现）

职业服务层
  ├─ JobHelperMcpClient（MCP 客户端，简历持久化与岗位服务）
  └─ CareerTaskService（异步职业任务，如岗位推荐）

模型层
  ├─ ChatHandler（Spring AI function calling，模型自主调用底层工具）
  ├─ PlainTextLlmService（纯文本 LLM 调用，用于任务规划与聊天）
  └─ LlmToolRegistry（@Tool 注解自动注册）

工具层（由 chat 模型通过 function calling 自主调用）
  ├─ TimeTools             时间查询
  ├─ WeatherTools          天气查询
  ├─ WebSearchTools        网络搜索
  ├─ RagFlowTools          知识库检索 / 问答 / 上传 / 文档管理
  ├─ ReminderTools         提醒设置
  ├─ MemoryTools           记忆功能
  ├─ MailTools             邮件发送
  └─ SummaryTools          对话总结

高层工具（由 Agent 编排器直接调度）
  ├─ ChatToolHandler        对话处理（模型内部自主调用底层工具）
  ├─ VoiceSynthesizeToolHandler  语音合成（含口语化润色与超时重试）
  ├─ ImageGenerateToolHandler    图片生成
  ├─ VideoGenerateToolHandler    视频生成
  ├─ CareerResume*ToolHandler    简历保存 / 取回 / 清除 / 分析 / 评分
  ├─ CareerJob*ToolHandler       岗位搜索 / 推荐
  └─ KnowledgeFileRetrieveToolHandler  知识库文件取回

外部服务层
  ├─ DeepSeek / 火山方舟 / OpenAI   AI 对话与推理
  ├─ SiliconFlow / 火山方舟 / OpenAI 图片生成
  ├─ 火山方舟 Seedance / OpenAI Sora / 阿里云 DashScope 视频生成
  ├─ 豆包语音 seed-audio-1.0       语音合成
  ├─ 博查搜索                       网络搜索
  ├─ 心知天气                       天气查询
  ├─ RAGFlow（Docker）              知识库检索 / 文档管理 / 向量存储
  ├─ JobHelper（MCP）               简历持久化 / 岗位数据 / 推荐服务
  └─ SMTP 邮件服务                  邮件发送

存储层
  ├─ MySQL（Flyway 迁移）   租户、账号、会话、Bot、消息、记忆等持久化
  └─ JobHelper 服务端存储    简历原始文件（PDF）与岗位数据
```

### 架构设计原则

**工具是大模型的手**：底层工具（天气、搜索、邮件等）通过 Spring AI function calling 注册，由 chat 模型在对话过程中自主决定调用时机和顺序。Agent 编排层只负责高层任务拆解（如"对话 → 语音输出"），不干预底层工具调用。

**安全第一**：实现 API 密钥认证、用户白名单验证、CORS 限制等多重安全措施，保护系统免受未授权访问。

**异步处理**：采用异步消息处理机制，避免主线程阻塞，提高系统响应性和吞吐量。

**弹性设计**：实现自动重连机制、重试逻辑和优雅降级，确保系统在异常情况下仍能正常运行。

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+
- MySQL 8+（Flyway 自动迁移建表）
- Docker（用于运行 RAGFlow 知识库服务，可选）
- 微信 ILink SDK 账号
- 至少 4GB 可用内存（推荐 8GB+）

### 1. 启动 RAGFlow 知识库服务（可选）

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

### 2. 克隆项目

```bash
git clone https://github.com/xiaodust/wx-claw.git

cd wx-claw
```

### 3. 配置文件

```bash
cp wx-claw-backfront/src/main/resources/application.example.yml \
   wx-claw-backfront/src/main/resources/application.yml
```

### 4. 填写配置

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

### 5. 运行后端

```bash
cd wx-claw-backfront
mvn spring-boot:run
```

后端启动时 Flyway 自动执行数据库迁移（`db/migration/V*.sql`），监听 `http://localhost:8080`。

### 6. 运行用户端（官网 + 控制台）

```bash
cd wx-claw-user
npm install
npm run dev
```

浏览器访问 `http://localhost:3001`：`/` 为官网主页，注册后进入用户控制台。

### 7. 运行管理端

管理端用于查看 Bot 实时状态、对话历史和模型原始调用记录：

```bash
cd wx-claw-admin
npm install
npm run dev
```

浏览器访问 `http://localhost:3000`，支持两种登录方式：

- **管理员账号**：用户名密码登录（默认用户名 `admin`，密码由 `ADMIN_PASSWORD` 提供；未配置时首次启动自动生成并打印到日志）
- **API Key**：`<credentialId>.<secret>` 格式的管理 Key（Bootstrap 凭据 `*` scope 可访问管理端）

### 8. 初始化注册链路

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

### 本地 Docker 运行

```powershell
copy .env.example .env
docker compose up -d --build
```

访问：

- 管理端：http://localhost:3000
- 用户端：http://localhost:3001

后端和 MySQL 不直接暴露公网，只通过前端 Nginx 反代 `/api`。

### 关键环境变量

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

### 使用 cpolar 临时公网访问

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

### 用户发件邮箱配置

AI 通过 `send_email` 工具发邮件时，使用当前租户在前端设置页配置的 SMTP 邮箱。
后端 `.env` 中的系统邮箱仅用于登录验证码和密码重置。

## 配置说明

### 环境变量

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

### 性能配置

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

### 知识库配置

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

## 项目结构

```
wx-claw/
├── wx-claw-backfront/                 # Spring Boot 后端
│   └── src/main/java/com/dust/wxclawbackfront/
│       ├── bot/                       # Bot 业务
│       │   ├── agent/                 # Agent 编排系统
│       │   │   ├── model/             # 数据模型（AgentContext, AgentResult, TaskPlan, TaskStep, TaskResult, MediaAttachment）
│       │   │   ├── orchestrator/      # 编排器（AgentOrchestrator, PlanValidator）
│       │   │   │   ├── executor/      # 任务执行器（TaskExecutor，多步媒体合并）
│       │   │   │   └── tool/          # 工具注册与处理器
│       │   │   │       └── handler/   # 具体工具实现（Chat, Voice, Image, Video, Career, KnowledgeFile）
│       │   │   ├── prompt/            # 提示词加载器（模板变量与条件段渲染）
│       │   │   ├── career/            # 职业助手（简历上下文、查询规范化、任务服务）
│       │   │   ├── mcp/               # MCP 客户端（jobhelper 简历与岗位服务）
│       │   │   ├── llm/               # 对话处理（ChatHandler, PlainTextLlmService）+ 图片/语音/视频
│       │   │   └── tools/             # AI 底层工具
│       │   │       ├── time/          # 时间工具
│       │   │       ├── weather/       # 天气工具
│       │   │       ├── search/        # 搜索工具
│       │   │       ├── reminder/      # 提醒工具
│       │   │       ├── summary/       # 总结工具
│       │   │       ├── memory/        # 记忆工具
│       │   │       ├── mail/          # 邮件工具
│       │   │       ├── ragflow/       # 知识库工具
│       │   │       └── shared/        # 共享组件
│       │   ├── api/                   # REST API
│       │   ├── dao/                   # 数据访问层
│       │   ├── knowledge/             # 知识库业务
│       │   ├── ragflow/               # RAGFlow 客户端
│       │   ├── service/               # 业务服务
│       │   └── scheduler/             # 定时任务
│       ├── ilink/                     # ILink 接入
│       ├── tenancy/                   # 多租户与账号体系
│       │   ├── entity/                # Tenant / TenantAccount / TenantSession /
│       │   │                          # TenantApiCredential / TenantInviteCode / TenantEmailVerification
│       │   ├── service/               # 注册 / 登录 / 找回 / 邮箱验证 / 邀请码
│       │   ├── security/              # API Key 认证、PBKDF2 哈希、限流
│       │   └── api/                   # 公开接口（/api/public/*）
│       ├── user/                      # 用户控制台接口（/api/user/*）
│       └── admin/                     # 管理端接口（/api/admin/*）
│       └── config/                    # 配置类
│   └── src/main/resources/
│       ├── ai/prompts/                # Agent 编排提示词模板（agent-planner.md）
│       ├── ai/skills/                 # Spring AI 技能定义
│       └── db/migration/              # Flyway 数据库迁移（V1..V31）
├── wx-claw-user/                      # Vue 3 用户端（官网 + 控制台，端口 3001）
├── wx-claw-admin/                     # Vue 3 只读管理端
└── docs/                              # 文档
```

## 工具开发

### 添加新的底层工具

创建工具类并实现 `AiToolProvider` 接口，Spring AI 会自动通过 function calling 注册：

```java
@Component
public class MyTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 80;  // 控制注册顺序
    }

    @Tool(name = "my_tool", description = "我的工具描述")
    public MyResult doSomething(String param) {
        // 实现逻辑
    }
}
```

完成后无需修改其他文件，chat 模型会自动发现并可通过 function calling 调用。

### 添加新的高层编排工具

实现 `ToolHandler` 接口，Agent 编排器会自动发现：

```java
@Component
public class MyToolHandler implements ToolHandler {

    @Override
    public String getName() {
        return "my_tool";
    }

    @Override
    public TaskResult execute(TaskStep step, AgentContext context) {
        // 实现逻辑
        return TaskResult.success("结果", executionTimeMs);
    }
}
```

### 编排提示词维护

Agent 规划提示词统一存放在 `wx-claw-backfront/src/main/resources/ai/prompts/`，由 `PromptLoader` 每次调用实时加载：修改提示词无需改代码、无需重启即可生效（生产环境建议配合 CI 校验）：

```text
ai/prompts/
  └── agent-planner.md    # 编排模型提示词模板
```

模板支持：

- **变量替换** - `{{user_message}}`、`{{#history}}` 等占位符注入用户消息与对话历史
- **条件段** - `{{#career_enabled}}...{{/career_enabled}}` 按功能开关渲染职业工具与规则
- **快速失败** - 文件缺失、变量缺失、条件段未闭合都会直接抛异常，避免静默生成错误提示词

硬编码意图识别已移除：所有消息统一交给规划模型，由模型根据提示词自行判断是否调用工具、拆解步骤并提取岗位参数；规划失败时降级为普通对话。

### 工具执行顺序

| Order | 工具             |
| ----- | -------------- |
| 10    | TimeTools      |
| 20    | WeatherTools   |
| 30    | WebSearchTools |
| 35    | RagFlowTools   |
| 40    | ReminderTools  |
| 50    | MemoryTools    |
| 60    | MailTools      |
| 70    | SummaryTools   |

## API 接口

### 公开接口（/api/public/*，无需凭据）

| 接口 | 说明 |
| --- | --- |
| `POST /tenants/register` | 租户自助注册（邀请码 + 邮箱验证码 + 用户名密码） |
| `POST /auth/email-code` | 发送邮箱验证码（purpose: REGISTER / SETUP / RESET） |
| `POST /auth/login` | 用户名密码登录，返回会话 token |
| `POST /auth/forgot-password` | 申请密码重置（发重置链接到邮箱） |
| `POST /auth/reset-password` | 用重置链接设置新密码 |

### 用户控制台接口（/api/user/*，需会话 token 或 API Key）

| 接口 | 说明 |
| --- | --- |
| `GET /bots` / `POST /bots` / `DELETE /bots/{botId}` | Bot 列表 / 创建 / 删除 |
| `GET /bots/{botId}/qr` | 扫码连接二维码 |
| `GET /bots/{botId}/conversations` 等 | 会话与聊天记录 |
| `GET /ai-config` / `PUT /ai-config/{cap}` 等 | 各能力 API Key 与模型配置 |
| `GET /account` | 当前租户账号信息（hasAccount） |
| `POST /account/setup` | 为无账号租户创建控制台账号 |
| `POST /account/password` | 修改密码（吊销全部会话） |

### 管理端接口（/api/admin/*，需 admin:invite 等权限）

| 接口 | 说明 |
| --- | --- |
| `GET /overview` / `GET /bots` / `GET /conversations` | 运行总览 / Bot 状态 / 对话与调用审计 |
| `GET /invite-codes` / `POST /invite-codes` / `DELETE /invite-codes/{code}` | 邀请码列表 / 生成 / 停用 |

### 多媒体能力

#### 图片生成

- **服务商**: SiliconFlow / 火山方舟 / OpenAI（用户控制台可切换并配置各自 Key）
- **模型**: Kolors、豆包文生图、OpenAI 图片模型等

#### 视频生成

- **服务商**: 火山方舟 Seedance / OpenAI Sora / 阿里云 DashScope（通义万相）

#### 语音合成

- **API**: 火山引擎豆包语音（openspeech.bytedance.com）
- **模型**: seed-audio-1.0（需在豆包语音控制台开通服务，欠费/未开通返回 403）

#### 对话模型

- **服务商**: DeepSeek / 火山方舟 / OpenAI / 自定义兼容端点
- **模型**: 由用户控制台按服务商选择或自定义输入

## 常见问题

### Q: 如何更换 AI 模型？

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

### Q: 如何关闭某个功能？

在 `application.yml` 中设置对应的 `enabled: false`：

```yaml
wxclaw:
  reminder:
    enabled: false
  mail:
    enabled: false
```

### Q: 如何查看日志？

修改日志级别：

```yaml
logging:
  level:
    com.dust.wxclawbackfront: debug
```

## 更新日志

### v3.0 (当前版本)

- **平台化** - 新增官网主页与租户自助注册，注册从后端配置改为自助流程
- **邀请码注册制** - 新增邀请码表与管理端生成/停用能力，支持配额与有效期，原子扣减防超发
- **邮箱验证** - 注册邮箱必填，发送 6 位验证码校验邮箱归属（10 分钟单次有效）
- **账号密码登录** - 新增控制台账号与会话体系，token 只存 SHA-256、7 天过期；用户端登录改为纯密码，API Key 仅用于接口调用与一次性激活账号
- **密码管理** - 忘记密码走邮箱重置链接（30 分钟单次有效），登录后支持修改密码并吊销全部会话
- **用户控制台** - 自助创建/删除 Bot、扫码连接、聊天记录、多能力 API Key 与模型配置
- **管理端完善** - 运行总览、Bot 状态、对话与调用审计、注册邀请码管理
- **多服务商模型** - 对话/图片/视频支持 DeepSeek、火山方舟、OpenAI、SiliconFlow、阿里云 DashScope，模型目录含免费标记与自定义输入
- **安全加固** - PBKDF2 密码哈希、登录/注册/验证码限流、防用户枚举（伪哈希比对）、Scope 权限收敛
- **工业暗色主题** - 官网、注册、登录、用户控制台统一视觉
- **TTS 诊断** - 语音失败透出服务端错误码与可执行提示（未开通/欠费/Key 无效）

### v2.1

- **编排提示词文件化** - 规划提示词从 Java 代码迁移至 `ai/prompts/agent-planner.md`，支持变量替换与 career 条件段，缺失即快速失败
- **意图识别交还模型** - 删除 `requiresHighLevelPlanning` 等硬编码关键词判断，所有消息统一交规划模型，规划失败统一降级 chat
- **岗位参数模型化** - 规划模型按 `steps.params.input` 分句并提取岗位参数，职业工具处理器据此回填缺失参数或追问
- **职业助手完善** - 简历保存/取回/清除/分析/评分、岗位搜索与推荐接入 JobHelper MCP，无简历时返回友好提示
- **多媒体附件** - 多步骤任务的多条媒体附件合并后逐条发送（图片/语音/视频）
- **语音可靠性** - TTS 增加超时重试与最大尝试次数配置（`wxclaw.tts.*`）
- **消息保序** - 消息处理按用户分区串行消费，同一用户消息不乱序，不同用户并行
- **评测与回归** - 新增编排黄金调度用例与提示词离线评测 runner（默认排除于常规 CI）
- **岗位结果修复** - 岗位压缩结果不再携带完整描述与任职要求

### v2.0

- **Agent 编排系统** - 引入 LLM 驱动的任务编排，支持多步骤任务自动拆解和执行
- **Spring AI Function Calling** - 底层工具由 chat 模型通过 function calling 自主调用，工具真正成为"大模型的手"
- **架构重构** - 清晰的三层架构：编排层(Orchestrator) → 执行层(TaskExecutor) → 工具层(ToolHandler)
- **代码精简** - 移除 trace 系统、正则意图检测器、冗余接口方法，日志替代 trace
- **消息防抖** - 3 秒内相同消息去重，防止重复调用 AI
- **用户记忆修复** - 修复 ThreadLocal 跨线程丢失 userId 导致记忆加载失败的问题
- **语音合成优化** - 文本口语化润色从 ChatToolHandler 移入 VoiceSynthesizeToolHandler，职责清晰
- **安全增强** - 实现 API 密钥认证和限制 CORS 来源
- **用户验证** - 添加用户验证白名单机制
- **架构改进** - 统一构造器注入模式和条件注解使用
- **工具优化** - 移除冗余依赖，提升性能
- **消息处理** - 实现异步消息处理，防止阻塞
- **连接增强** - 改进 ILink 重连机制，提高稳定性
- **上下文传播** - 确保异步线程中用户上下文的一致性
- **防抖改进** - 使用 SHA-256 哈希改进消息去重
- **AI 规划** - 增强 Agent 规划验证和重试机制
- **媒体处理** - 实现图像理解失败时的优雅降级
- **异常处理** - 创建自定义异常层次结构，提供更好错误反馈
- **数据库优化** - 改善 SQLite 并发处理和重试机制

### v1.1

- 新增 RAGFlow 知识库集成
  - 知识库检索和问答
  - 文件上传到知识库（支持微信直接发送文件）
  - 文档管理（列举、删除、更新）
- 新增智能文档发送功能
  - 长文本自动转换为文件发送
  - 支持 TXT 和 Markdown 格式
  - 优化长回复的阅读体验

### v1

- 初始版本发布
- 支持微信 ILink 接入
- 实现基础对话功能
- 集成多种 AI 工具
- 支持图片生成和语音合成
- 实现责任链模式的工具注册

## 许可证

[Apache-2.0 License](LICENSE)
