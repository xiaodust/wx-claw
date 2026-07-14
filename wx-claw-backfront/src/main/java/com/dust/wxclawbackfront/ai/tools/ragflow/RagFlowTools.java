package com.dust.wxclawbackfront.ai.tools.ragflow;

import com.dust.wxclawbackfront.ai.ragflow.RagFlowClient;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAGFlow 知识库工具
 * 提供知识库检索和问答功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RagFlowClient.class)
public class RagFlowTools implements AiToolProvider {

    private final RagFlowClient ragFlowClient;
    private final AiToolInvocationStore invocationStore;

    @Override
    public Object getTool() {
        return this;
    }

    @Override
    public int getOrder() {
        return 35; // 在搜索工具之后
    }

    /**
     * 从知识库中检索信息
     */
    @Tool(name = "knowledge_search",
          description = "【仅检索】从知识库中搜索相关文档片段。仅当需要查看知识库原始内容时使用，返回的是未处理的文档片段。注意：回答用户问题请使用 knowledge_ask，不要使用此工具。")
    public KnowledgeSearchResult searchKnowledge(String query, int topK) {
        log.info("AI调用 knowledge_search: query={}, topK={}", query, topK);

        if (query == null || query.isBlank()) {
            return new KnowledgeSearchResult(false, List.of(), "搜索关键词不能为空");
        }

        if (topK <= 0) {
            topK = 3;
        }

        List<RagFlowClient.SearchResult> results = ragFlowClient.search(query, topK);

        List<KnowledgeItem> items = results.stream()
                .map(r -> new KnowledgeItem(r.content(), r.documentName(), r.similarity()))
                .collect(Collectors.toList());

        String message = items.isEmpty() ? "未找到相关知识库内容" : "找到 " + items.size() + " 条相关内容";

        invocationStore.add("knowledge_search", "query=" + query, message);

        return new KnowledgeSearchResult(true, items, message);
    }

    /**
     * 向知识库提问
     */
    @Tool(name = "knowledge_ask",
          description = "向知识库提问并获取智能回答。仅当用户明确表示要查询知识库、或问题明显涉及知识库中可能存储的特定信息（如产品说明、文档内容、FAQ等）时调用。普通闲聊、通用问题不要调用。参数question直接传入用户的问题原文。")
    public KnowledgeAskResult askKnowledge(String question) {
        log.info("AI调用 knowledge_ask: question={}", question);

        if (question == null || question.isBlank()) {
            return new KnowledgeAskResult(false, null, "问题不能为空");
        }

        RagFlowClient.RagFlowResult result = ragFlowClient.ask(question);

        String message = result.isSuccess() ? "已从知识库获取回答" : result.error();

        invocationStore.add("knowledge_ask", "question=" + question, message);

        return new KnowledgeAskResult(result.isSuccess(), result.content(), message);
    }

    /**
     * 知识库搜索结果
     */
    public record KnowledgeSearchResult(boolean success, List<KnowledgeItem> items, String message) {}

    /**
     * 知识库条目
     */
    public record KnowledgeItem(String content, String documentName, Double similarity) {}

    /**
     * 知识库问答结果
     */
    public record KnowledgeAskResult(boolean success, String content, String message) {}
}
