> 如何新增底层工具、高层编排工具与维护编排提示词。
# 工具开发

## 添加新的底层工具

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

## 添加新的高层编排工具

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

## 编排提示词维护

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

## 工具执行顺序

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


