package com.dust.wxclawbackfront.bot.scheduler;

/**
 * 判断任务失败是否值得重试。
 *
 * <p>默认按可重试处理（网络、超时等临时故障）；仅对明确的永久性错误
 * （参数非法、找不到执行器、目标不存在等）判定为不可重试，避免无效重试。</p>
 */
public final class RetryableErrorClassifier {

    private RetryableErrorClassifier() {
    }

    public static boolean isRetryable(Throwable error) {
        if (error == null) {
            return true;
        }
        if (error instanceof IllegalArgumentException) {
            return false;
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return true;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("未找到执行器")
                || lower.contains("不能为空")
                || lower.contains("不存在")
                || lower.contains("not found")
                || lower.contains("invalid")
                || lower.contains("illegal")) {
            return false;
        }
        return true;
    }
}
