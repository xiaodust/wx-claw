package com.dust.wxclawbackfront.bot.agent.tools.shared;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolLoopGuardTest {
    @Test
    void blocksRepeatedAndExcessiveCalls() {
        AgentToolLoopGuard guard = new AgentToolLoopGuard();
        guard.begin(3, 1, Duration.ofSeconds(1));
        guard.check("search", "q=a");
        assertThatThrownBy(() -> guard.check("search", "q=a")).hasMessageContaining("重复");
        guard.clear();

        guard.begin(1, 2, Duration.ofSeconds(1));
        guard.check("search", "q=a");
        assertThatThrownBy(() -> guard.check("weather", "city=a")).hasMessageContaining("次数");
        guard.clear();
    }
}
