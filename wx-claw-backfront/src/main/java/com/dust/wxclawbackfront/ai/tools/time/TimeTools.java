package com.dust.wxclawbackfront.ai.tools.time;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TimeTools implements AiToolProvider {

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    private final TimeHandler timeHandler;
    private final AiToolInvocationStore invocationStore;

    public TimeTools(TimeHandler timeHandler, AiToolInvocationStore invocationStore) {
        this.timeHandler = timeHandler;
        this.invocationStore = invocationStore;
    }

    @Tool(name = "time_now", description = "获取当前时间（默认 Asia/Shanghai）。仅当用户明确询问'现在几点'、'当前时间'、'今天日期'、'星期几'等时间相关问题时使用。注意：闲聊问候如'在干嘛'、'你好'等不要调用此工具。")
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

