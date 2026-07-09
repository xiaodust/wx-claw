package com.dust.wxclawbackfront.ai.tools.web;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSearchTools {

    private final BochaWebSearchHandler searchHandler;
    private final AiToolInvocationStore invocationStore;

    public WebSearchTools(BochaWebSearchHandler searchHandler, AiToolInvocationStore invocationStore) {
        this.searchHandler = searchHandler;
        this.invocationStore = invocationStore;
    }

    @Tool(name = "web_search", description = "联网搜索全网网页信息。适合查询新闻、时效性信息、百科、公开网页资料。参数 query 为搜索词；freshness 可选 noLimit、oneDay、oneWeek、oneMonth、oneYear；count 建议 1 到 10。")
    public WebSearchToolResult search(String query, String freshness, Integer count) {
        BochaWebSearchResult result = searchHandler.search(query, freshness, count);
        String response = searchHandler.formatReply(result);
        invocationStore.add("web_search", "query=" + query + ",freshness=" + freshness + ",count=" + count, response);
        if (result == null) {
            return new WebSearchToolResult(query, freshness, count, null, "联网搜索失败");
        }
        return new WebSearchToolResult(result.getQuery(), result.getFreshness(), result.getCount(), result.getItems(), result.getErrorMsg());
    }

    public record WebSearchToolResult(String query,
                                      String freshness,
                                      Integer count,
                                      List<BochaWebSearchResult.Item> items,
                                      String errorMsg) {
    }
}
