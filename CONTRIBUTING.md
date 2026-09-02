# 贡献指南

感谢考虑为 WX-Claw 做贡献！请先阅读本指南，保持仓库一致。

## 开发环境

- JDK 21+
- Node 20+
- Maven 3.9+（或直接使用 `mvnw`）
- MySQL 8+
- 推荐 IDE：IntelliJ IDEA / VS Code

## 提交规范

- 标题（commit subject）：`<scope>: <imperative summary>`，如 `agent: 新增 XX 工具` / `docs: 修正 README`
- 范围（scope）示例：`agent`、`bot`、`tenancy`、`security`、`docs`、`ci`
- Body 简述动机与实现要点；关联 Issue 用 `#123`

## 分支与 PR

- 从 `main` 切出新分支：`git checkout -b feat/your-feature`
- 一个 PR 只做一件事；改动大先在 Issue 讨论
- PR 描述要回答：为什么改、怎么改、如何测试

## 代码风格

- 后端遵循项目内现有 Java 代码风格（构造器注入、final 字段、按域分包）
- 前端使用 `npm run lint` / `npm run format`（项目内 ESLint / Prettier 配置为准）
- 提交前跑一遍后端测试：`./mvnw test`

## 文档与目录约定

- `docs/FEATURES.md` 功能清单
- `docs/ARCHITECTURE.md` 架构与目录
- `docs/DEPLOYMENT.md` 部署与环境变量
- `docs/API.md` 对外接口
- `docs/EXTENDING.md` 如何新增工具与动作
- `docs/PROJECT_DOCUMENTATION.md` 维护者全案
- `CHANGELOG.md` 版本变更

## 提交内容禁区

- 真实密钥、口令、私域地址、个人凭据
- 涉及他人版权或商标的内容（除非明确授权）
- 改动 Flyway 已发布迁移的 SQL 文本（会破坏已部署库的校验和）

## 行为准则

请遵循 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。