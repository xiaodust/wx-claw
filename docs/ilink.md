# ILink 集成指南

本文档介绍 WX-Claw 如何通过微信 ILink SDK 接入微信，实现消息收发功能。

## 目录

- [概述](#概述)
- [架构设计](#架构设计)
- [核心组件](#核心组件)
- [工作流程](#工作流程)
- [配置说明](#配置说明)
- [登录流程](#登录流程)
- [消息处理](#消息处理)
- [消息发送](#消息发送)
- [状态恢复](#状态恢复)
- [常见问题](#常见问题)

## 概述

WX-Claw 使用微信 ILink SDK 实现微信消息的收发。ILink 是一个非官方微信协议库，通过模拟微信客户端的方式接入微信服务器。

### 特性

- 支持文本、图片、语音消息接收
- 支持发送文本、图片、文件消息
- 支持登录状态持久化，重启无需重新扫码
- 支持多用户并发消息处理

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        微信服务器                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ILinkClient (SDK)                             │
│              负责协议通信、消息收发、登录管理                       │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   ILinkRuntimeManager   │     │  ILinkMessageDispatcher  │
│   (生命周期管理)         │     │     (消息分发处理)        │
└─────────────────────────┘     └─────────────────────────┘
              │                               │
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   ResumeContextStore    │     │  ILinkUserInputExtractor │
│   (登录状态持久化)       │     │     (消息解析)           │
└─────────────────────────┘     └─────────────────────────┘
                                          │
                                          ▼
                              ┌─────────────────────────┐
                              │   ILinkMessageSender     │
                              │     (消息发送)           │
                              └─────────────────────────┘
```

## 核心组件

### 1. ILinkRuntimeManager

负责 ILinkClient 的生命周期管理，包括创建、登录、关闭。

**路径**: `ilnk/runtime/ILinkRuntimeManager.java`

**职责**:
- 创建 ILinkClient 实例
- 执行登录流程（扫码/恢复）
- 管理活跃客户端实例
- 处理关闭和状态保存

### 2. ILinkMessageDispatcher

入站消息处理器，是整个消息处理的核心。

**路径**: `ilnk/inbound/ILinkMessageDispatcher.java`

**职责**:
- 接收并分发用户消息
- 识别消息意图（文本/图片/语音/命令）
- 调用相应的处理器（AI对话/图片生成/语音合成）
- 管理对话上下文

### 3. ILinkUserInputExtractor

消息解析器，将微信消息转换为统一的输入格式。

**路径**: `ilnk/ILinkUserInputExtractor.java`

**职责**:
- 解析文本消息
- 解析图片消息（含图片理解）
- 解析语音消息（文字识别）
- 提取消息文本内容

### 4. ILinkMessageSender

消息发送器，统一封装发送逻辑。

**路径**: `ilnk/outbound/ILinkMessageSender.java`

**职责**:
- 发送文本消息
- 发送图片消息
- 发送文件消息（语音等）

## 工作流程

### 消息接收流程

```
1. 微信服务器推送消息
         │
         ▼
2. ILinkClient 接收消息
         │
         ▼
3. ILinkMessageDispatcher.dispatch()
         │
         ├── 检查是否是 # 命令 ──→ CommandHandler 处理
         │
         ├── 检查是否是新建对话指令
         │
         └── 正常消息处理
                  │
                  ▼
4. ILinkUserInputExtractor.extract()
         │
         ├── 文本消息 ──→ ILinkUserInput.text()
         ├── 图片消息 ──→ 图片理解 ──→ ILinkUserInput.image()
         └── 语音消息 ──→ 语音识别 ──→ ILinkUserInput.text()
                  │
                  ▼
5. 意图识别
         │
         ├── 生图意图 ──→ ImageGenerationHandler
         ├── 语音意图 ──→ VolcTtsHandler
         └── 普通对话 ──→ ChatHandler
                  │
                  ▼
6. 处理结果
         │
         ├── 保存消息到数据库（异步）
         ├── 记录 Trace（异步）
         └── 发送回复给用户
```

### 消息类型处理

| 消息类型 | 处理方式 |
|---------|---------|
| 文本消息 | 直接提取文字，发送给 AI |
| 图片消息 | 下载图片 → 图片理解 → 生成描述 |
| 语音消息 | 微信自动识别为文字 → 提取文字 |
| `#` 命令 | 不经过 AI，直接返回预设内容 |

## 配置说明

### application.yml

```yaml
wxclaw:
  ilink:
    # 是否启用 ILink 监听
    monitor:
      enabled: true
    
    # 连接超时（毫秒）
    connect-timeout-ms: 15000
    
    # 读取超时（毫秒）
    read-timeout-ms: 35000
    
    # 写入超时（毫秒）
    write-timeout-ms: 15000
    
    # 登录超时（毫秒）
    login-timeout-ms: 180000
```

### 配置项说明

| 配置项 | 默认值 | 说明 |
|-------|--------|------|
| `monitor.enabled` | `true` | 是否启用 ILink 消息监听 |
| `connect-timeout-ms` | `15000` | 连接超时时间 |
| `read-timeout-ms` | `35000` | 读取超时时间 |
| `write-timeout-ms` | `15000` | 写入超时时间 |
| `login-timeout-ms` | `180000` | 登录超时时间（3分钟） |

## 登录流程

### 首次登录

```
1. 启动应用
         │
         ▼
2. ILinkRuntimeManager.createAndLogin()
         │
         ▼
3. 检查是否有保存的 ResumeContext
         │
         ├── 无 ──→ 创建新的 ILinkClient
         │              │
         │              ▼
         │         执行扫码登录
         │              │
         │              ▼
         │         输出二维码到控制台
         │              │
         │              ▼
         │         等待用户扫码
         │              │
         │              ▼
         │         登录成功
         │
         └── 有 ──→ 尝试恢复登录状态
                       │
                       ├── 成功 ──→ 直接使用
                       │
                       └── 失败 ──→ 重新扫码
```

### 扫码登录

```java
// 输出二维码
String qrCodeContent = client.executeLogin();
log.info("请扫码登录：\n{}", qrCodeContent);

// 等待登录完成
client.getLoginFuture().get();
log.info("iLink 登录成功");
```

### 登录状态保存

登录成功后，系统会自动保存 `ResumeContext`，包含：
- 登录上下文（token等）
- 所有用户的 context token

保存路径：应用运行目录下的持久化文件。

## 消息处理

### 文本消息

```java
// 提取文本
String text = userInputExtractor.extractText(msg);

// 创建输入
ILinkUserInput input = ILinkUserInput.text(text);
```

### 图片消息

```java
// 1. 解析图片
WechatCdnMediaService.ResolvedImage resolved = cdnMediaService.resolveImage(client, item);

// 2. 图片理解
ImageUnderstandingResult result = imageHandler.understandByUrl(url, userText);

// 3. 创建输入
ILinkUserInput input = ILinkUserInput.image(url, model, description, requestJson, error);
```

### 语音消息

```java
// 微信服务端已做语音识别，直接提取文字
String voiceText = item.getVoice_item().getText();
```

### 命令处理

以 `#` 开头的消息不经过 AI，直接返回预设内容：

```java
if (commandHandler.isCommand(userText)) {
    String reply = commandHandler.handle(userText);
    messageSender.sendText(userId, reply);
    return;
}
```

支持的命令：
- `#help` / `#帮助` - 显示帮助
- `#tools` / `#工具` - 显示功能列表
- `#version` / `#版本` - 显示版本信息

## 消息发送

### 发送文本

```java
messageSender.sendText(userId, "你好！");
```

### 发送图片

```java
messageSender.sendImage(userId, imageBytes, "image.png", "这是生成的图片");
```

### 发送文件

```java
messageSender.sendFile(userId, audioBytes, "voice.wav", "这是语音回复");
```

## 状态恢复

### ResumeContext

`ResumeContext` 用于保存和恢复登录状态，避免每次重启都需要重新扫码。

**保存时机**:
- 正常关闭应用时
- ILinkClient 关闭前

**恢复时机**:
- 应用启动时
- 创建新的 ILinkClient 前

### 持久化存储

```java
// 保存
ResumeContext context = client.exportResumeContext();
resumeContextStore.save(context);

// 加载
ResumeContext resumeContext = resumeContextStore.load();

// 删除（登录过期时）
resumeContextStore.delete();
```

### 恢复失败处理

如果 ResumeContext 恢复失败（如登录过期），系统会：
1. 删除旧的 ResumeContext
2. 重新执行扫码登录流程

## 常见问题

### Q: 如何重新登录？

1. 停止应用
2. 删除持久化的 ResumeContext 文件
3. 重新启动应用

### Q: 登录超时怎么办？

检查网络连接，或增加 `login-timeout-ms` 配置值。

### Q: 消息收不到？

1. 检查 `monitor.enabled` 是否为 `true`
2. 检查 ILinkClient 是否登录成功
3. 查看日志是否有错误信息

### Q: 如何切换账号？

1. 停止应用
2. 删除 ResumeContext 文件
3. 重新启动并用新账号扫码登录

### Q: 支持群聊吗？

当前版本仅支持私聊消息，不支持群聊。

## 相关文档

- [ILink SDK GitHub](https://github.com/lith0924/wechat-ilink-sdk-java)
- [项目 README](../README.md)
