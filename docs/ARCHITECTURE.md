> 系统架构、技术选型与项目结构说明（维护者/贡献者向）。
# 技术架构

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

## 架构设计原则

**工具是大模型的手**：底层工具（天气、搜索、邮件等）通过 Spring AI function calling 注册，由 chat 模型在对话过程中自主决定调用时机和顺序。Agent 编排层只负责高层任务拆解（如"对话 → 语音输出"），不干预底层工具调用。

**安全第一**：实现 API 密钥认证、用户白名单验证、CORS 限制等多重安全措施，保护系统免受未授权访问。

**异步处理**：采用异步消息处理机制，避免主线程阻塞，提高系统响应性和吞吐量。

**弹性设计**：实现自动重连机制、重试逻辑和优雅降级，确保系统在异常情况下仍能正常运行。


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


