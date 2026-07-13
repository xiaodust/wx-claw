# WX-Claw AI 助手

一个基于 Spring AI 的智能对话助手，支持微信 ILink 接入，提供多种 AI 工具能力。

## 版本信息

当前版本：**v1**

## 功能特性

### 核心功能

- **智能对话** - 基于大模型的自然语言对话
- **工具调用** - AI 自动选择并调用合适的工具完成任务
- **上下文记忆** - 支持多轮对话，记住上下文
- **用户画像** - 记住用户偏好和习惯，提供个性化服务

### 工具能力

| 功能 | 说明 |
|------|------|
| 时间查询 | 获取当前时间、日期、星期 |
| 天气查询 | 查询任意城市实时天气和预报 |
| 网络搜索 | 搜索最新资讯、新闻、百科 |
| 提醒设置 | 一次性或周期性提醒（每天/每周/每月） |
| 对话总结 | 生成日报、周报、月报 |
| 邮件发送 | 发送邮件通知 |
| 图片生成 | 根据描述生成图片（SiliconFlow Kolors） |
| 语音回复 | TTS 语音合成 |

### 快捷命令

| 命令 | 说明 |
|------|------|
| `#help` / `#帮助` | 显示帮助信息 |
| `#tools` / `#工具` | 查看功能列表 |
| `#version` / `#版本` | 显示版本信息 |

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                      微信 ILink                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  ILinkMessageDispatcher                      │
│            (消息分发、意图识别、图片/语音处理)                  │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ ChatHandler│   │ImageGen  │   │VoiceTTS  │
        │ (AI对话)  │   │(图片生成) │   │(语音合成) │
        └──────────┘   └──────────┘   └──────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                    LlmToolRegistry                           │
│              (工具自动发现 - 责任链模式)                       │
└─────────────────────────────────────────────────────────────┘
              │
    ┌─────────┼─────────┬─────────┬─────────┬─────────┐
    ▼         ▼         ▼         ▼         ▼         ▼
┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐
│ Time  │ │Weather│ │Search │ │Reminder│ │Memory │ │Summary│
└───────┘ └───────┘ └───────┘ └───────┘ └───────┘ └───────┘
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- 微信 ILink SDK 账号

### 1. 克隆项目

```bash
# GitLab 仓库
git clone https://codeserver.youkeda.com/dustheart/wx-claw.git

# 或 GitHub 镜像
git clone https://github.com/xiaodust/wx-claw.git

cd wx-claw
```

### 2. 配置文件

```bash
cp wx-claw-backfront/src/main/resources/application.example.yml \
   wx-claw-backfront/src/main/resources/application.yml
```

### 3. 填写配置

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
```

### 4. 运行项目

```bash
cd wx-claw-backfront
mvn spring-boot:run
```

## 配置说明

### 环境变量

| 变量名 | 说明 |
|--------|------|
| `AI_API_KEY` | AI 模型 API 密钥 |
| `AI_BASE_URL` | AI 模型 API 地址 |
| `SILICONFLOW_API_KEY` | SiliconFlow 图片生成密钥 |
| `TTS_API_KEY` | TTS 语音合成密钥 |
| `BOCHA_API_KEY` | 博查搜索密钥 |
| `SENIVERSE_KEY` | 心知天气密钥 |
| `MAIL_USERNAME` | 邮箱账号 |
| `MAIL_PASSWORD` | 邮箱授权码 |

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
│       │   ├── tools/             # AI 工具
│       │   │   ├── time/          # 时间工具
│       │   │   ├── weather/       # 天气工具
│       │   │   ├── search/        # 搜索工具
│       │   │   ├── reminder/      # 提醒工具
│       │   │   ├── summary/       # 总结工具
│       │   │   ├── memory/        # 记忆工具
│       │   │   ├── mail/          # 邮件工具
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

2. 完成！无需修改其他文件，Spring 会自动发现并注册。

### 工具执行顺序

| Order | 工具 |
|-------|------|
| 10 | TimeTools |
| 20 | WeatherTools |
| 30 | WebSearchTools |
| 40 | ReminderTools |
| 50 | MemoryTools |
| 60 | MailTools |
| 70 | SummaryTools |

## API 接口

### 图片生成

- **API**: SiliconFlow
- **模型**: Kwai-Kolors/Kolors
- **文档**: https://api-docs.siliconflow.cn/docs/api/images-generations-post

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

### v1 (当前版本)

- 初始版本发布
- 支持微信 ILink 接入
- 实现基础对话功能
- 集成多种 AI 工具
- 支持图片生成和语音合成
- 实现责任链模式的工具注册

## 许可证

[MIT License](LICENSE)
