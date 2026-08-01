package com.dust.wxclawbackfront.bot.agent.llm;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmToolRegistryTest {
    @Test
    void excludesHighLevelOnlyProvidersFromChat() {
        Object chatTool = new Object();
        Object highLevelTool = new Object();
        AiToolProvider visible = provider(chatTool, true);
        AiToolProvider hidden = provider(highLevelTool, false);

        assertThat(new LlmToolRegistry(List.of(visible, hidden)).getAllTools()).containsExactly(chatTool);
    }

    private AiToolProvider provider(Object tool, boolean visible) {
        return new AiToolProvider() {
            @Override public Object getTool() { return tool; }
            @Override public boolean isAvailableToChat() { return visible; }
        };
    }
}
