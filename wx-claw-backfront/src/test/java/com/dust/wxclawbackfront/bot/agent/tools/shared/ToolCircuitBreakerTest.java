package com.dust.wxclawbackfront.bot.agent.tools.shared;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCircuitBreakerTest {

    private final ToolCircuitBreaker.MutableClock clock =
            new ToolCircuitBreaker.MutableClock(1_000_000L);

    private ToolCircuitBreaker breaker(int threshold, long cooldownSeconds) {
        return new ToolCircuitBreaker(clock, true, true, threshold, cooldownSeconds, true, 100, 120);
    }

    private ToolCircuitBreaker breaker(int globalThreshold, long globalCooldownSeconds,
                                       int userThreshold, long userCooldownSeconds) {
        return new ToolCircuitBreaker(clock, true, true, globalThreshold, globalCooldownSeconds,
                true, userThreshold, userCooldownSeconds);
    }

    @Test
    void opensAfterConsecutiveFailures() {
        ToolCircuitBreaker breaker = breaker(3, 60);

        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");
        breaker.checkAllowed("web_search");
        breaker.recordFailure("web_search");

        assertThatThrownBy(() -> breaker.checkAllowed("web_search"))
                .isInstanceOf(ToolCircuitOpenException.class)
                .hasMessageContaining("暂时不可用");
    }

    @Test
    void successResetsFailureCount() {
        ToolCircuitBreaker breaker = breaker(3, 60);

        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");
        breaker.recordSuccess("web_search");
        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");

        // 成功清零后，再次连续失败 2 次（未到 3）不应打开
        breaker.checkAllowed("web_search");
        breaker.recordFailure("web_search");
        assertThatThrownBy(() -> breaker.checkAllowed("web_search"))
                .isInstanceOf(ToolCircuitOpenException.class);
    }

    @Test
    void allowsProbeAfterCooldownAndClosesOnSuccess() {
        ToolCircuitBreaker breaker = breaker(2, 60);
        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");
        assertThat(breaker.isOpen("web_search")).isTrue();

        clock.advance(Duration.ofSeconds(61).toMillis());

        // 冷却结束：放行一个探测请求
        breaker.checkAllowed("web_search");
        assertThat(breaker.isOpen("web_search")).isFalse();

        // 探测成功 → 闭合，之后正常放行
        breaker.recordSuccess("web_search");
        breaker.checkAllowed("web_search");
        breaker.checkAllowed("web_search");
    }

    @Test
    void reopensWhenProbeFails() {
        ToolCircuitBreaker breaker = breaker(2, 60);
        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");

        clock.advance(Duration.ofSeconds(61).toMillis());
        breaker.checkAllowed("web_search");
        breaker.recordFailure("web_search");

        assertThat(breaker.isOpen("web_search")).isTrue();
        assertThatThrownBy(() -> breaker.checkAllowed("web_search"))
                .isInstanceOf(ToolCircuitOpenException.class);
    }

    @Test
    void blocksAllCallsWhileOpenAndCountsToolIndependently() {
        ToolCircuitBreaker breaker = breaker(2, 60);
        breaker.recordFailure("weather_now");
        breaker.recordFailure("weather_now");

        assertThatThrownBy(() -> breaker.checkAllowed("weather_now"))
                .isInstanceOf(ToolCircuitOpenException.class);
        // 其他工具不受影响
        breaker.checkAllowed("web_search");
    }

    @Test
    void detectsFailureByErrorMsgConvention() {
        ToolCircuitBreaker breaker = breaker(2, 60);
        record ErrorMsgResult(String errorMsg) {}
        record OkResult(String text) {}

        assertThat(breaker.isFailureResult(new ErrorMsgResult("网络错误"))).isTrue();
        assertThat(breaker.isFailureResult(new ErrorMsgResult(""))).isFalse();
        assertThat(breaker.isFailureResult(new OkResult("ok"))).isFalse();
    }

    @Test
    void detectsFailureBySuccessBooleanConvention() {
        ToolCircuitBreaker breaker = breaker(2, 60);
        record FlagResult(boolean success, String message) {}

        assertThat(breaker.isFailureResult(new FlagResult(false, "失败"))).isTrue();
        assertThat(breaker.isFailureResult(new FlagResult(true, "ok"))).isFalse();
    }

    @Test
    void disabledBreakerNeverBlocks() {
        ToolCircuitBreaker breaker = new ToolCircuitBreaker(clock, false, true, 1, 60, true, 1, 60);

        breaker.recordFailure("web_search");
        breaker.recordFailure("web_search");
        breaker.checkAllowed("web_search");
    }

    @Test
    void userDimensionIsolatesMaliciousUserOnly() {
        ToolCircuitBreaker breaker = breaker(20, 60, 3, 120);
        String userA = "tenant-a::bot-1::userA";
        String userB = "tenant-a::bot-1::userB";

        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);

        // 用户级阈值 3：userA 被隔离
        assertThatThrownBy(() -> breaker.checkAllowed("web_search", userA))
                .isInstanceOf(ToolCircuitOpenException.class)
                .hasMessageContaining("暂时不可用");
        // userB 不受影响
        breaker.checkAllowed("web_search", userB);
        breaker.recordSuccess("web_search", userB);
        breaker.checkAllowed("web_search", userB);
        // 全局未打开（3 次 < 20）
        assertThat(breaker.isOpen("web_search")).isFalse();
    }

    @Test
    void userIsolationStopsAccumulatingToGlobal() {
        ToolCircuitBreaker breaker = breaker(20, 60, 3, 120);
        String userA = "tenant-a::bot-1::userA";

        // 恶意用户贡献 3 次失败后被隔离，后续调用在 checkAllowed 阶段被拦截，
        // 不会继续向全局累计
        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);
        assertThatThrownBy(() -> breaker.checkAllowed("web_search", userA))
                .isInstanceOf(ToolCircuitOpenException.class);
        assertThat(breaker.isOpen("web_search")).isFalse();

        // 全局阈值 20：还需 17 个不同用户各失败一次（3 + 17 = 20）
        for (int i = 0; i < 17; i++) {
            breaker.recordFailure("web_search", "tenant-a::bot-1::user" + i);
        }
        assertThat(breaker.isOpen("web_search")).isTrue();
    }

    @Test
    void userSuccessResetsUserCounter() {
        ToolCircuitBreaker breaker = breaker(20, 60, 3, 120);
        String userA = "tenant-a::bot-1::userA";

        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);
        breaker.recordSuccess("web_search", userA);
        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);

        breaker.checkAllowed("web_search", userA);
        assertThat(breaker.isOpen("web_search")).isFalse();
    }

    @Test
    void userCooldownAllowsProbeAndReopensOnFailure() {
        ToolCircuitBreaker breaker = breaker(20, 60, 2, 120);
        String userA = "tenant-a::bot-1::userA";

        breaker.recordFailure("web_search", userA);
        breaker.recordFailure("web_search", userA);
        assertThatThrownBy(() -> breaker.checkAllowed("web_search", userA))
                .isInstanceOf(ToolCircuitOpenException.class);

        clock.advance(Duration.ofSeconds(121).toMillis());
        breaker.checkAllowed("web_search", userA);

        breaker.recordFailure("web_search", userA);
        assertThatThrownBy(() -> breaker.checkAllowed("web_search", userA))
                .isInstanceOf(ToolCircuitOpenException.class);
    }
}
