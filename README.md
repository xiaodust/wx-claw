# WX-Claw AI 助手

一个基于 Spring AI 的智能对话助手，支持微信 ILink 接入，提供多种 AI 工具能力。

## 版本信息

当前版本：**v1.1**

## 功能特性

### 核心功能

- **智能对话** - 基于大模型的自然语言对话
- **工具调用** - AI 自动选择并调用合适的工具完成任务
- **上下文记忆** - 支持多轮对话，记住上下文
- **用户画像** - 记住用户偏好和习惯，提供个性化服务
- **知识库管理** - 集成 RAGFlow 知识库，支持文档检索和问答
- **智能文档发送** - 长文本自动转换为文件发送，优化阅读体验

### 工具能力

| 功能          | 说明                           |
| ----------- | ---------------------------- |
| 时间查询        | 获取当前时间、日期、星期                 |
| 天气查询        | 查询任意城市实时天气和预报                |
| 网络搜索        | 搜索最新资讯、新闻、百科                 |
| 提醒设置        | 一次性或周期性提醒（每天/每周/每月）          |
| 对话总结        | 生成日报、周报、月报                   |
| 邮件发送        | 发送邮件通知                       |
| 图片生成        | 根据描述生成图片（SiliconFlow Kolors） |
| 语音回复        | TTS 语音合成                     |
| **知识库检索**   | 从知识库中搜索相关文档片段                |
| **知识库问答**   | 向知识库提问并获取智能回答                |
| **知识库上传**   | 上传文件到知识库（PDF、DOCX、TXT等）      |
| **知识库文档管理** | 列举、删除、更新知识库中的文档              |

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
接入层
  └─ 微信 ILink SDK

消息分发层
  └─ ILinkMessageDispatcher
       ├─ 消息解析（文本 / 图片 / 语音 / 文件）
       ├─ 意图识别（图片生成 / 语音回复）
       └─ 文件处理（下载文件 → 上传知识库）

处理器层
  ├─ ChatHandler          AI 对话（工具调用、上下文记忆、用户画像）
  ├─ ImageHandler          图片生成 / 图片理解
  ├─ DocumentGenerator     长文本转文件发送
  └─ VolcTtsHandler        语音合成

工具注册层
  └─ LlmToolRegistry       责任链模式，自动发现并注册 AiToolProvider

工具层
  ├─ TimeTools             时间查询
  ├─ WeatherTools          天气查询
  ├─ WebSearchTools        网络搜索
  ├─ RagFlowTools          知识库检索 / 问答 / 上传 / 文档管理
  ├─ ReminderTools         提醒设置
  ├─ MemoryTools           记忆功能
  ├─ MailTools             邮件发送
  └─ SummaryTools          对话总结

外部服务层
  ├─ 火山引擎 / OpenAI 兼容模型    AI 推理
  ├─ SiliconFlow Kolors             图片生成
  ├─ 博查搜索                       网络搜索
  ├─ 心知天气                       天气查询
  ├─ RAGFlow（Docker）              知识库检索 / 文档管理 / 向量存储
  └─ SMTP 邮件服务                  邮件发送

存储层
  └─ SQLite                会话、消息、提醒、记忆等本地持久化
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Docker（用于运行 RAGFlow 知识库服务，可选）
- 微信 ILink SDK 账号

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
# GitLab 仓库
git clone https://codeserver.youkeda.com/dustheart/wx-claw.git

# 或 GitHub 镜像
git clone https://github.com/xiaodust/wx-claw.git

cd wx-claw
```

### 3. 配置文件

```bash
cp wx-claw-backfront/src/main/resources/application.example.yml \
   wx-claw-backfront/src/main/resources/application.yml
```

### 4. 填写配置

编辑 `application.yml`，配置以下必要参数：

```yaml
spring:
  ai:
    openai:
      api-key: your-api-key        # AI 模型 API 密钥

wxclaw:
  ai:
    image:
      generate:
        api-key: your-key           # SiliconFlow 图片生成密钥
    tts:
      api-key: your-key             # TTS 语音合成密钥
    web-search:
      bocha:
        api-key: your-key           # 博查搜索密钥
    weather:
      seniverse:
        key: your-key               # 心知天气密钥
  ragflow:
    enabled: true                   # 启用知识库功能
    base-url: http://localhost:9380 # RAGFlow API 地址
    api-key: your-ragflow-key       # RAGFlow API 密钥
    dataset-id: your-dataset-id     # 知识库 ID
    chat-id: your-chat-id           # 聊天助手 ID
```

### 5. 运行项目

```bash
cd wx-claw-backfront
mvn spring-boot:run
```

## 配置说明

### 环境变量

| 变量名                   | 说明                 |
| --------------------- | ------------------ |
| `AI_API_KEY`          | AI 模型 API 密钥       |
| `AI_BASE_URL`         | AI 模型 API 地址       |
| `SILICONFLOW_API_KEY` | SiliconFlow 图片生成密钥 |
| `TTS_API_KEY`         | TTS 语音合成密钥         |
| `BOCHA_API_KEY`       | 博查搜索密钥             |
| `SENIVERSE_KEY`       | 心知天气密钥             |
| `MAIL_USERNAME`       | 邮箱账号               |
| `MAIL_PASSWORD`       | 邮箱授权码              |
| `RAGFLOW_API_KEY`     | RAGFlow 知识库密钥      |
| `RAGFLOW_BASE_URL`    | RAGFlow 服务地址       |

### 性能配置

```yaml
wxclaw:
  ai:
    chat:
      max-rounds: 5        # 工具调用最大轮数
      max-tokens: 512      # 最大输出 token
      timeout: PT15S       # 超时时间
    context:
      max-chars: 4000      # 上下文最大字符数
    document:
      enabled: true        # 启用文档发送功能
      threshold: 1000      # 触发文档发送的字符阈值
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
├── wx-claw-backfront/
│   └── src/main/java/com/dust/wxclawbackfront/
│       ├── ai/
│       │   ├── chat/              # 对话处理
│       │   ├── image/             # 图片生成
│       │   ├── voice/             # 语音合成
│       │   ├── ragflow/           # RAGFlow 知识库客户端
│       │   ├── document/          # 文档生成
│       │   ├── tools/             # AI 工具
│       │   │   ├── time/          # 时间工具
│       │   │   ├── weather/       # 天气工具
│       │   │   ├── search/        # 搜索工具
│       │   │   ├── reminder/      # 提醒工具
│       │   │   ├── summary/       # 总结工具
│       │   │   ├── memory/        # 记忆工具
│       │   │   ├── mail/          # 邮件工具
│       │   │   ├── ragflow/       # 知识库工具
│       │   │   └── shared/        # 共享组件
│       │   └── service/           # 业务服务
│       ├── ilnk/                  # ILink 接入
│       └── config/                # 配置类
└── docs/                          # 文档
```

## 工具开发

### 添加新工具

1. 创建工具类并实现 `AiToolProvider` 接口：

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

1. 完成！无需修改其他文件，Spring 会自动发现并注册。

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

### 图片生成

- **API**: SiliconFlow
- **模型**: Kwai-Kolors/Kolors
- **文档**: <https://api-docs.siliconflow.cn/docs/api/images-generations-post>

### 语音合成

- **API**: 火山引擎 TTS
- **模型**: seed-audio-1.0

## 常见问题

### Q: 如何更换 AI 模型？

修改 `application.yml` 中的配置：

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
wxclaw:
  log:
    level: debug
```

## 更新日志

### v1.1 (当前版本)

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

[MIT License](LICENSE)
