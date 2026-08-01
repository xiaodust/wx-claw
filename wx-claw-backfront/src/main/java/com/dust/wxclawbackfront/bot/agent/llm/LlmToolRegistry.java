package com.dust.wxclawbackfront.bot.agent.llm;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 工具注册器
 * 自动发现所有实现 AiToolProvider 接口的工具
 */
@Slf4j
@Component
public class LlmToolRegistry {

    private final List<AiToolProvider> toolProviders;

    /**
     * Spring 自动注入所有实现 AiToolProvider 接口的 Bean
     */
    public LlmToolRegistry(List<AiToolProvider> toolProviders) {
        this.toolProviders = toolProviders.stream()
                .filter(AiToolProvider::isAvailableToChat)
                .sorted(Comparator.comparingInt(AiToolProvider::getOrder))
                .collect(Collectors.toList());
        
        log.info("已注册 {} 个工具提供者: {}", 
                this.toolProviders.size(),
                this.toolProviders.stream()
                        .map(p -> p.getClass().getSimpleName() + "(order=" + p.getOrder() + ")")
                        .collect(Collectors.joining(", ")));
    }

    /**
     * 获取所有已注册的工具
     * @return 工具数组
     */
    public Object[] getAllTools() {
        return toolProviders.stream()
                .map(AiToolProvider::getTool)
                .toArray();
    }
}
