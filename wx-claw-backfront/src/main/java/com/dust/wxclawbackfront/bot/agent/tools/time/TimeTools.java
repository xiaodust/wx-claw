package com.dust.wxclawbackfront.bot.agent.tools.time;

import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.ToolInvocationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
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

    @Tool(name = "time_now", description = "获取当前时间（默认 Asia/Shanghai）。仅当用户明确询问'现在几点'、'当前时间'、'今天日期'、'星期几'等时间相关问题时使用。注意：闲聊问候如'在干嘛'、'你好'等不要调用此工具。")
    @ToolInvocationLog("time_now")
    public TimeToolResult now() {
        TimeResult result = timeHandler.now();
        if (result == null) {
            return new TimeToolResult("Asia/Shanghai", null, null);
        }
        return new TimeToolResult(result.getZoneId(), result.getIsoTime(), result.getFormattedTime());
    }

    public record TimeToolResult(String zoneId, String isoTime, String formattedTime) {
    }
}

