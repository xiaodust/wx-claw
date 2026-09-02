> 对外 HTTP 接口一览（公开/用户/管理端）。
# API 接口

## 公开接口（/api/public/*，无需凭据）

| 接口 | 说明 |
| --- | --- |
| `POST /tenants/register` | 租户自助注册（邀请码 + 邮箱验证码 + 用户名密码） |
| `POST /auth/email-code` | 发送邮箱验证码（purpose: REGISTER / SETUP / RESET） |
| `POST /auth/login` | 用户名密码登录，返回会话 token |
| `POST /auth/forgot-password` | 申请密码重置（发重置链接到邮箱） |
| `POST /auth/reset-password` | 用重置链接设置新密码 |

## 用户控制台接口（/api/user/*，需会话 token 或 API Key）

| 接口 | 说明 |
| --- | --- |
| `GET /bots` / `POST /bots` / `DELETE /bots/{botId}` | Bot 列表 / 创建 / 删除 |
| `GET /bots/{botId}/qr` | 扫码连接二维码 |
| `GET /bots/{botId}/conversations` 等 | 会话与聊天记录 |
| `GET /ai-config` / `PUT /ai-config/{cap}` 等 | 各能力 API Key 与模型配置 |
| `GET /account` | 当前租户账号信息（hasAccount） |
| `POST /account/setup` | 为无账号租户创建控制台账号 |
| `POST /account/password` | 修改密码（吊销全部会话） |

## 管理端接口（/api/admin/*，需 admin:invite 等权限）

| 接口 | 说明 |
| --- | --- |
| `GET /overview` / `GET /bots` / `GET /conversations` | 运行总览 / Bot 状态 / 对话与调用审计 |
| `GET /invite-codes` / `POST /invite-codes` / `DELETE /invite-codes/{code}` | 邀请码列表 / 生成 / 停用 |

## 多媒体能力

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


