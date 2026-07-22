package com.dust.wxclawbackfront.bot.agent.tools.reminder.executor;

import com.dust.wxclawbackfront.bot.dao.entity.ReminderTask;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchHandler;
import com.dust.wxclawbackfront.bot.agent.tools.search.BochaWebSearchResult;
import com.dust.wxclawbackfront.ilink.outbound.ILinkMessageSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 联网查询推送执行器
 * 定时进行网络搜索并推送结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchPushExecutor implements TaskActionExecutor {
    
    private final BochaWebSearchHandler searchHandler;
    private final ILinkMessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean execute(ReminderTask task) {
        try {
            String actionParams = task.getActionParams();
            if (actionParams == null || actionParams.isBlank()) {
                log.error("网络搜索任务参数为空: taskId={}", task.getId());
                return false;
            }
            
            // 解析参数
            JsonNode params = objectMapper.readTree(actionParams);
            String query = params.get("query").asText();
            String freshness = params.has("freshness") ? params.get("freshness").asText() : "noLimit";
            int count = params.has("count") ? params.get("count").asInt() : 5;
            
            log.info("执行网络搜索任务: taskId={}, userId={}, query={}", 
                    task.getId(), task.getUserId(), query);
            
            // 执行搜索
            BochaWebSearchResult searchResult = searchHandler.search(query, freshness, count);
            
            // 格式化消息
            String message = formatSearchMessage(query, searchResult);
            
            // 发送消息
            messageSender.sendText(task.getUserId(), message);
            
            log.info("网络搜索推送成功: taskId={}, userId={}, query={}", task.getId(), task.getUserId(), query);
            return true;
            
        } catch (Exception e) {
            log.error("网络搜索任务执行异常: taskId={}, error={}", task.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String getActionType() {
        return "WEB_SEARCH_PUSH";
    }
    
    /**
     * 格式化搜索结果消息
     */
    private String formatSearchMessage(String query, BochaWebSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔍 搜索结果：").append(query).append("\n\n");
        
        if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
            sb.append("❌ 搜索失败：").append(result.getErrorMsg());
            return sb.toString();
        }
        
        if (result.getItems() == null || result.getItems().isEmpty()) {
            sb.append("未找到相关结果");
            return sb.toString();
        }
        
        int index = 1;
        for (BochaWebSearchResult.Item item : result.getItems()) {
            sb.append(index++).append(". ").append(item.getName()).append("\n");
            if (item.getSnippet() != null && !item.getSnippet().isBlank()) {
                sb.append("   ").append(item.getSnippet()).append("\n");
            }
            if (item.getUrl() != null && !item.getUrl().isBlank()) {
                sb.append("   🔗 ").append(item.getUrl()).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
