package com.dust.wxclawbackfront.bot.agent.tools.shared;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class AgentToolLoopGuard {
    private final ThreadLocal<State> state = new ThreadLocal<>();

    public void begin(int maxToolCalls, int maxRepeatedCalls, Duration hardTimeout) {
        long timeoutMs = hardTimeout == null ? 60_000 : Math.max(1, hardTimeout.toMillis());
        state.set(new State(Math.max(1, maxToolCalls), Math.max(1, maxRepeatedCalls),
                System.currentTimeMillis() + timeoutMs));
    }

    public void check(String toolName, String arguments) {
        State current = state.get();
        if (current == null) return;
        verifyWithinDeadline();
        if (++current.totalCalls > current.maxToolCalls) {
            throw new IllegalStateException("工具调用次数超过限制: " + current.maxToolCalls);
        }
        String signature = toolName + "|" + arguments;
        int repeats = current.signatures.merge(signature, 1, Integer::sum);
        if (repeats > current.maxRepeatedCalls) {
            throw new IllegalStateException("检测到重复工具调用: " + toolName);
        }
    }

    public void verifyWithinDeadline() {
        State current = state.get();
        if (current != null && System.currentTimeMillis() > current.deadlineMs) {
            throw new IllegalStateException("Agent 执行超过总时限");
        }
    }

    public void clear() {
        state.remove();
    }

    private static final class State {
        private final int maxToolCalls;
        private final int maxRepeatedCalls;
        private final long deadlineMs;
        private final Map<String, Integer> signatures = new HashMap<>();
        private int totalCalls;

        private State(int maxToolCalls, int maxRepeatedCalls, long deadlineMs) {
            this.maxToolCalls = maxToolCalls;
            this.maxRepeatedCalls = maxRepeatedCalls;
            this.deadlineMs = deadlineMs;
        }
    }
}
