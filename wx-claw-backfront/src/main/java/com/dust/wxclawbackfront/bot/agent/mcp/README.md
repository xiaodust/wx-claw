# Agent MCP Clients

该目录集中管理 WX-Claw 作为 MCP Client 访问外部能力的所有代码。

## 目录约定

每个 MCP Server 使用独立子目录：

```text
mcp/
  jobhelper/
    JobHelperMcpClient.java
    JobHelperMcpConfiguration.java
    JobHelperMcpException.java
    dto/
```

后续接入其他 MCP Server 时创建新的同级目录，不把 MCP SDK、传输配置或协议 DTO 放入业务 `integration` 包。

## 分层约定

- MCP Client：负责连接选择、协议生命周期、Tools/Resources 调用和协议结果转换。
- Agent/业务工具：负责用户意图参数、身份映射、异步任务和结果展示。
- MCP Server：负责领域业务和长期数据持久化。
- WX-Claw 不为 Job Helper 保留私有 HTTP Client 或 HTTP 回退分支。

当配置多个 MCP Server 时，客户端必须根据初始化返回的 `serverInfo.name` 选择目标服务，不能依赖 Spring 注入列表顺序。
