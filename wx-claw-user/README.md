# wx-claw-user 用户控制台

面向用户的微信 Bot 自助管理页面（Vue 3 + Vite + Element Plus）。

## 功能

- 创建自己的 Bot，创建后展示二维码，扫码连接（微信登录）
- 查看 Bot 运行状态、会话列表与聊天记录
- 配置自己的模型 API Key，覆盖后端默认 Key；清除后自动回退

## 使用

```bash
npm install
npm run dev        # http://localhost:3001
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`（后端默认端口），如后端端口不同请修改
`vite.config.ts` 中的 proxy 目标。

登录需要输入平台 API Key（格式 `credentialId.secret`），该 Key 需具备以下权限：

- `userbot:read` / `userbot:write`：创建、管理自己的 Bot
- `conversation:read`：查看聊天记录
- `aiconfig:read` / `aiconfig:write`：配置自己的 LLM API Key

首次使用可直接用后端配置的 bootstrap key（具备 `*` 全部权限）。
