package com.dust.wxclawbackfront.ai.agent.detector;

import com.dust.wxclawbackfront.ai.agent.ToolRequirement;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.time.TimeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 时间工具需求检测器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeToolRequirementDetector implements ToolRequirementDetector {

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(现在|此刻|当前)?(几点|时间|日期|几号|星期几|周几)|今天(几号|日期)|今天是(几号|什么日期)");

    private final TimeHandler timeHandler;

    @Override
    public ToolRequirement detect(String userMessage, Set<String> calledTools) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (calledTools.contains("time_now")) {
            return null;
        }
        if (!TIME_PATTERN.matcher(userMessage).find()) {
            return null;
        }
        return new ToolRequirement("time_now", null, null, null);
    }

    @Override
    public AiToolInvocationStore.Invocation fillTool(ToolRequirement requirement) {
        try {
            var result = timeHandler.now();
            return new AiToolInvocationStore.Invocation(
                    "time_now",
                    "无参数",
                    result.toString()
            );
        } catch (Exception e) {
            log.warn("补调 time_now 失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getToolName() {
        return "time_now";
    }
}
