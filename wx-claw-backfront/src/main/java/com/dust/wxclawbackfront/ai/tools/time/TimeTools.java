package com.dust.wxclawbackfront.ai.tools.time;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TimeTools {

    private final TimeHandler timeHandler;
    private final AiToolInvocationStore invocationStore;

    public TimeTools(TimeHandler timeHandler, AiToolInvocationStore invocationStore) {
        this.timeHandler = timeHandler;
        this.invocationStore = invocationStore;
    }

    @Tool(name = "time_now", description = "获取当前时间（默认 Asia/Shanghai）。当用户询问“现在几点/当前时间/今天日期/星期几”等时使用。")
    public TimeToolResult now() {
        TimeResult result = timeHandler.now();
        String response = result == null ? null : result.getReplyText();
        invocationStore.add("time_now", null, response);
        if (result == null) {
            return new TimeToolResult("Asia/Shanghai", null, null);
        }
        return new TimeToolResult(result.getZoneId(), result.getIsoTime(), result.getFormattedTime());
    }

    public record TimeToolResult(String zoneId, String isoTime, String formattedTime) {
    }
}

