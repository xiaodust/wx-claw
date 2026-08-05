package com.dust.wxclawbackfront.bot.agent.tools.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 双维度工具熔断器。
 *
 * <p>与 {@link AgentToolLoopGuard}（请求内：次数/同参/硬超时）互补，负责跨请求的
 * 持久熔断。维度分为两级：</p>
 * <ul>
 *   <li><b>用户级</b>（{@code tenantId::botId::userId::toolName}）：防止个别用户
 *   恶意/异常输入反复触发同一工具失败，拖垮整个服务；</li>
 *   <li><b>全局级</b>（{@code toolName}）：兜底服务端依赖故障（API key 失效、外部服务整体不可用），
 *   阈值更高，且单用户被隔离后不再向全局累计失败。</li>
 * </ul>
 *
 * <p>两级共用 CLOSED → OPEN → HALF_OPEN 状态机：冷却期内拦截，冷却结束放行一个
 * 探测请求，成功闭合、失败重新打开。当前为内存实现，单实例有效；多实例部署时需迁移 Redis。</p>
 */
@Slf4j
@Component
public class ToolCircuitBreaker {

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private static final Duration HALF_OPEN_PROBE_WINDOW = Duration.ofSeconds(30);

    private final Clock clock;
    private final boolean enabled;
    private final boolean countErrorMsgFailures;

    private final int globalFailureThreshold;
    private final Duration globalCooldown;

    private final boolean userEnabled;
    private final int userFailureThreshold;
    private final Duration userCooldown;

    private final ConcurrentMap<String, Entry> globalEntries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Entry> userEntries = new ConcurrentHashMap<>();

    @Autowired
    public ToolCircuitBreaker(
            @Value("${wxclaw.agent.tool-circuit-breaker.enabled:true}") boolean enabled,
            @Value("${wxclaw.agent.tool-circuit-breaker.count-error-msg-failures:true}") boolean countErrorMsgFailures,
            @Value("${wxclaw.agent.tool-circuit-breaker.global.failure-threshold:20}") int globalFailureThreshold,
            @Value("${wxclaw.agent.tool-circuit-breaker.global.cooldown-seconds:60}") long globalCooldownSeconds,
            @Value("${wxclaw.agent.tool-circuit-breaker.user.enabled:true}") boolean userEnabled,
            @Value("${wxclaw.agent.tool-circuit-breaker.user.failure-threshold:3}") int userFailureThreshold,
            @Value("${wxclaw.agent.tool-circuit-breaker.user.cooldown-seconds:120}") long userCooldownSeconds) {
        this(Clock.systemUTC(), enabled, countErrorMsgFailures,
                globalFailureThreshold, globalCooldownSeconds,
                userEnabled, userFailureThreshold, userCooldownSeconds);
    }

    ToolCircuitBreaker(Clock clock, boolean enabled, boolean countErrorMsgFailures,
                       int globalFailureThreshold, long globalCooldownSeconds,
                       boolean userEnabled, int userFailureThreshold, long userCooldownSeconds) {
        this.clock = clock;
        this.enabled = enabled;
        this.countErrorMsgFailures = countErrorMsgFailures;
        this.globalFailureThreshold = Math.max(1, globalFailureThreshold);
        this.globalCooldown = Duration.ofSeconds(Math.max(1, globalCooldownSeconds));
        this.userEnabled = userEnabled;
        this.userFailureThreshold = Math.max(1, userFailureThreshold);
        this.userCooldown = Duration.ofSeconds(Math.max(1, userCooldownSeconds));
    }

    /**
     * 工具执行前调用（无用户维度，仅全局判断）。
     */
    public void checkAllowed(String toolName) {
        checkAllowed(toolName, null);
    }

    /**
     * 记录一次成功（无用户维度，仅全局）。
     */
    public void recordSuccess(String toolName) {
        recordSuccess(toolName, null);
    }

    /**
     * 记录一次失败（无用户维度，仅全局）。
     */
    public void recordFailure(String toolName) {
        recordFailure(toolName, null);
    }

    /**
     * 工具执行前调用（带用户维度）。任一维度熔断打开都会抛 {@link ToolCircuitOpenException}。
     */
    public void checkAllowed(String toolName, String userKey) {
        if (!enabled) {
            return;
        }
        if (userKey != null && userEnabled) {
            checkEntry(userEntries, userKey + "::" + toolName, toolName,
                    userCooldown, "您对工具");
        }
        checkEntry(globalEntries, toolName, toolName, globalCooldown, "工具");
    }

    /**
     * 记录一次成功：闭合对应维度的熔断并清零连续失败计数。
     */
    public void recordSuccess(String toolName, String userKey) {
        if (!enabled) {
            return;
        }
        if (userKey != null && userEnabled) {
            closeEntry(userEntries, userKey + "::" + toolName, toolName);
        }
        closeEntry(globalEntries, toolName, toolName);
    }

    /**
     * 记录一次失败：用户级与全局级各累计一次；达到阈值打开对应维度熔断。
     */
    public void recordFailure(String toolName, String userKey) {
        if (!enabled) {
            return;
        }
        if (userKey != null && userEnabled) {
            recordFailureEntry(userEntries, userKey + "::" + toolName, toolName,
                    userCooldown, "用户级");
        }
        recordFailureEntry(globalEntries, toolName, toolName, globalCooldown, "全局");
    }

    /**
     * 判断工具返回结果是否代表失败（按项目两种约定：success=false 或 errorMsg 非空）。
     */
    public boolean isFailureResult(Object result) {
        if (result == null || !countErrorMsgFailures) {
            return false;
        }
        try {
            Object success = invokeAccessor(result, "success", "getSuccess", "isSuccess");
            if (success instanceof Boolean successFlag) {
                return !successFlag;
            }
            Object error = invokeAccessor(result,
                    "errorMsg", "getErrorMsg", "errorMessage", "getErrorMessage", "error", "getError");
            return error instanceof String errorText && !errorText.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 全局维度是否处于打开状态（观测/测试用）。
     */
    public boolean isOpen(String toolName) {
        return isOpen(globalEntries, toolName);
    }

    private void checkEntry(ConcurrentMap<String, Entry> entries, String key, String toolName,
                            Duration cooldown, String subject) {
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        long now = clock.millis();
        synchronized (entry) {
            if (entry.state == State.CLOSED) {
                return;
            }
            if (entry.state == State.OPEN) {
                if (now < entry.blockedUntilMillis) {
                    long retryAfterSeconds = Math.max(1,
                            (entry.blockedUntilMillis - now + 999) / 1000);
                    throw new ToolCircuitOpenException(
                            subject + " " + toolName + " 暂时不可用，约 " + retryAfterSeconds
                                    + " 秒后可重试，请改用其他方式");
                }
                entry.state = State.HALF_OPEN;
                entry.probeWindowUntilMillis = now + HALF_OPEN_PROBE_WINDOW.toMillis();
                log.warn("{} {} 熔断冷却结束，放行探测请求", subject, toolName);
                return;
            }
            if (now < entry.probeWindowUntilMillis) {
                long retryAfterSeconds = Math.max(1,
                        (entry.probeWindowUntilMillis - now + 999) / 1000);
                throw new ToolCircuitOpenException(
                        subject + " " + toolName + " 暂时不可用，约 " + retryAfterSeconds
                                + " 秒后可重试，请改用其他方式");
            }
            entry.probeWindowUntilMillis = now + HALF_OPEN_PROBE_WINDOW.toMillis();
        }
    }

    private void recordFailureEntry(ConcurrentMap<String, Entry> entries, String key, String toolName,
                                    Duration cooldown, String dimension) {
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        long now = clock.millis();
        synchronized (entry) {
            if (entry.state == State.HALF_OPEN) {
                open(entry, now, cooldown, toolName, dimension);
                return;
            }
            entry.consecutiveFailures++;
            if (entry.consecutiveFailures >= failureThreshold(dimension)) {
                open(entry, now, cooldown, toolName, dimension);
            }
        }
    }

    private void closeEntry(ConcurrentMap<String, Entry> entries, String key, String toolName) {
        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        synchronized (entry) {
            if (entry.state != State.CLOSED) {
                log.info("{} 熔断已闭合", toolName);
            }
            entry.state = State.CLOSED;
            entry.consecutiveFailures = 0;
        }
    }

    private void open(Entry entry, long now, Duration cooldown, String toolName, String dimension) {
        entry.state = State.OPEN;
        entry.blockedUntilMillis = now + cooldown.toMillis();
        log.warn("{} 工具 {} 连续失败 {} 次，{} 熔断 {} 秒", dimension, toolName,
                entry.consecutiveFailures, dimension, cooldown.toSeconds());
    }

    private int failureThreshold(String dimension) {
        return "用户级".equals(dimension) ? userFailureThreshold : globalFailureThreshold;
    }

    private boolean isOpen(ConcurrentMap<String, Entry> entries, String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return entry.state == State.OPEN
                    && clock.millis() < entry.blockedUntilMillis;
        }
    }

    private Object invokeAccessor(Object target, String... accessors) throws Exception {
        Class<?> type = target.getClass();
        for (String accessor : accessors) {
            try {
                Method method = type.getMethod(accessor);
                if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                    return method.invoke(target);
                }
            } catch (NoSuchMethodException ignored) {
                // 尝试下一个访问器
            }
        }
        return null;
    }

    private static final class Entry {
        private State state = State.CLOSED;
        private int consecutiveFailures;
        private long blockedUntilMillis;
        private long probeWindowUntilMillis;
    }

    /** 供测试使用的可变时钟 */
    static final class MutableClock extends Clock {
        private final ZoneId zone;
        private long millis;

        MutableClock(long millis) {
            this.zone = ZoneId.of("UTC");
            this.millis = millis;
        }

        void advance(long deltaMillis) {
            millis += deltaMillis;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId ignored) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
