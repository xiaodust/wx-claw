# WX-Claw 微信智能体平台

一个多租户的微信 ILink 智能体平台：基于 Spring AI 的 Agent 编排系统实现多工具协同调度与多模态回复，并配套官网、用户控制台与管理端三个 Web 端。

> [!IMPORTANT]
> **合规与免责声明**：本项目通过第三方微信接入通道（ILink）实现智能体消息收发，与腾讯公司及微信官方产品无任何关联，亦非官方 SDK。项目仅供技术学习与研究使用；使用者须自行评估并遵守微信平台相关服务条款、当地法律法规及所对接账号的服务协议。因使用本项目导致的账号限制、服务中断或其他风险，由使用者自行承担。

## 文档导航

> 本仓库文档已按读者拆分，首页只保留概览；细节请进入对应文档。

| 文档 | 内容 | 适合谁 |
| --- | --- | --- |
| [docs/FEATURES.md](docs/FEATURES.md) | 功能特性全量说明 | 想了解能力的产品/用户 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 技术架构、目录结构、设计原则 | 想理解系统的开发者 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | 安装、Docker 部署、环境变量、配置与 FAQ | 部署运维 |
| [docs/API.md](docs/API.md) | 公开/用户/管理端 HTTP 接口一览 | 对接方 |
| [docs/EXTENDING.md](docs/EXTENDING.md) | 新增底层工具与高层编排动作 | 二次开发 |
| [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md) | 项目开发与维护全案（数据库/安全/测试/待办） | 维护者/贡献者 |
| [CHANGELOG.md](CHANGELOG.md) | 版本历史 v1.0 → v3.0 | 所有人 |

## 核心特性

- **Agent 编排系统** - LLM 驱动的多步任务规划（`规划 → 校验重试 → 失败降级对话`），自动拆解复杂请求并按依赖执行
- **Function Calling 工具链** - chat 模型自主调用底层工具（时间/天气/搜索/提醒/记忆/邮件/摘要/知识库/职业），Agent 层放行 11 个高层动作，带用户级/全局双维度熔断
- **多级记忆** - 会话摘要 + 长期记忆抽取 + 向量召回，按信号触发避免每轮空转
- **多模态回复** - 文字 / 图片生成 / 视频生成 / 语音合成，多步骤媒体自动合并发送
- **多租户平台** - 租户级数据隔离、账号密码 + API Key 双体系、邀请码注册、邮箱验证、租户自配模型 Key
- **可观测与审计** - LLM 调用记录自动脱敏（密钥/Token/媒体），管理端可查 Bot 状态与调用审计
- **知识库问答** - 集成 RAGFlow：检索问答、文档上传、文件管理
- **职业助手（JobHelper）** - 简历解析/评分与"召回 → 评分 → 多样性重排 → Top N"推荐链路（MCP + HTTP 双通道）
- **消息管道** - 用户分区串行保序 + 三态回执 + 游标后置推进，崩溃窗口收敛为 at-least-once
- **定时能力** - 提醒（一次性/周期）、延时搜索/对话、周期性日报/天气推送
- **三端 Web** - 官网（含注册）+ 用户控制台 + 管理端，工业暗色主题

## 快速上手

环境要求：JDK 21+ / Maven、Node 18+、MySQL 8+（或直接使用 Docker）。

```bash
# 方式一：Docker（推荐）
cp .env.example .env          # 填入强密码/随机 Key
docker compose up -d --build
# 管理端 http://localhost:3000 / 用户端 http://localhost:3001

# 方式二：原生运行
cd wx-claw-backfront && mvn spring-boot:run     # 后端 :8080
cd wx-claw-user && npm install && npm run dev   # 用户端 :3001
cd wx-claw-admin && npm install && npm run dev  # 管理端 :3000
```

首次使用：管理端生成注册邀请码 → 用户端注册（邀请码 + 邮箱验证码）→ 登录后创建 Bot 扫码连接微信。完整步骤见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 技术栈

Java 21 · Spring Boot 4.1 · Spring AI 2.0 · MySQL 8 · Flyway · Vue 3 + TypeScript · Docker Compose

## 许可证

[Apache-2.0 License](LICENSE)
