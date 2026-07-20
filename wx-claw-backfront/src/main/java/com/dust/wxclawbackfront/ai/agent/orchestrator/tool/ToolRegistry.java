package com.dust.wxclawbackfront.ai.agent.orchestrator.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Agent 工具注册表
 * 自动发现并管理所有 ToolHandler 实现
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers;

    public ToolRegistry(List<ToolHandler> toolHandlers) {
        this.handlers = toolHandlers == null
                ? Map.of()
                : toolHandlers.stream().collect(Collectors.toMap(ToolHandler::getName, h -> h));
        log.info("已注册 {} 个 Agent 工具: {}", handlers.size(), handlers.keySet());
    }

    public Optional<ToolHandler> findHandler(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(toolName));
    }
}
