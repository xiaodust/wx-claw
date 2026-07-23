package com.dust.wxclawbackfront.bot.agent.tools.ragflow;

import com.dust.wxclawbackfront.bot.ragflow.RagFlowClient;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.ToolInvocationLog;
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
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();

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
    @ToolInvocationLog("knowledge_search")
    public KnowledgeSearchResult searchKnowledge(String query, int topK) {
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

        return new KnowledgeSearchResult(true, items, message);
    }

    /**
     * 向知识库提问
     */
    @Tool(name = "knowledge_ask",
          description = "向知识库提问并获取智能回答。仅当用户明确表示要查询知识库、或问题明显涉及知识库中可能存储的特定信息（如产品说明、文档内容、FAQ等）时调用。普通闲聊、通用问题不要调用。参数question直接传入用户的问题原文。")
    @ToolInvocationLog("knowledge_ask")
    public KnowledgeAskResult askKnowledge(String question) {
        if (question == null || question.isBlank()) {
            return new KnowledgeAskResult(false, null, "问题不能为空");
        }

        RagFlowClient.RagFlowResult result = ragFlowClient.ask(question);

        String message = result.isSuccess() ? "已从知识库获取回答" : result.error();

        return new KnowledgeAskResult(result.isSuccess(), result.content(), message);
    }

    /**
     * 上传文件到知识库
     */
    @Tool(name = "knowledge_upload",
          description = "上传文件到知识库。当用户要求上传文件到知识库时使用。参数fileUrl为文件的URL地址，fileName为文件名。支持的文件类型包括：PDF、DOCX、TXT、MD、CSV、XLSX等。")
    @ToolInvocationLog("knowledge_upload")
    public KnowledgeUploadResult uploadToKnowledge(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return new KnowledgeUploadResult(false, null, "文件URL不能为空");
        }

        if (fileName == null || fileName.isBlank()) {
            // 从URL中提取文件名
            fileName = extractFileNameFromUrl(fileUrl);
            if (fileName == null || fileName.isBlank()) {
                fileName = "uploaded_file";
            }
        }

        try {
            // 下载文件
            byte[] fileContent = downloadFile(fileUrl);

            // 上传到RAGFlow
            RagFlowClient.UploadResult result = ragFlowClient.uploadDocument(fileContent, fileName);

            String message = result.success() ? result.message() : "上传失败: " + result.message();

            return new KnowledgeUploadResult(result.success(), result.documentId(), message);

        } catch (Exception ex) {
            log.error("上传文件到知识库失败: {}", ex.getMessage(), ex);
            String errorMessage = "上传失败: " + ex.getMessage();
            return new KnowledgeUploadResult(false, null, errorMessage);
        }
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileNameFromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    return path.substring(lastSlash + 1);
                }
            }
        } catch (Exception e) {
            log.warn("从URL提取文件名失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 下载文件
     */
    private byte[] downloadFile(String fileUrl) throws Exception {
        java.net.URI uri = java.net.URI.create(fileUrl);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(uri)
                .timeout(java.time.Duration.ofSeconds(60))
                .GET()
                .build();

        java.net.http.HttpResponse<byte[]> response = httpClient.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("下载文件失败: HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * 列举知识库中的所有文档
     */
    @Tool(name = "knowledge_list_documents",
          description = "列举知识库中的所有文档。当用户要求查看知识库中有哪些文档时使用。返回文档列表，包含文档ID、名称、状态等信息。")
    @ToolInvocationLog("knowledge_list_documents")
    public KnowledgeDocumentListResult listKnowledgeDocuments() {
        List<RagFlowClient.DocumentInfo> documents = ragFlowClient.listDocuments();

        List<DocumentInfoItem> items = documents.stream()
                .map(d -> new DocumentInfoItem(d.id(), d.name(), d.status(), d.size(), d.chunkMethod()))
                .collect(Collectors.toList());

        String message = items.isEmpty() ? "知识库中没有文档" : "知识库中共有 " + items.size() + " 个文档";

        return new KnowledgeDocumentListResult(true, items, message);
    }

    /**
     * 删除知识库中的文档
     */
    @Tool(name = "knowledge_delete_document",
          description = "删除知识库中的文档。当用户要求删除知识库中的某个文档时使用。参数documentId为要删除的文档ID，可通过knowledge_list_documents获取。")
    @ToolInvocationLog("knowledge_delete_document")
    public KnowledgeDeleteResult deleteKnowledgeDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return new KnowledgeDeleteResult(false, "文档ID不能为空");
        }

        RagFlowClient.DeleteResult result = ragFlowClient.deleteDocuments(List.of(documentId));

        String message = result.success() ? result.message() : "删除失败: " + result.message();

        return new KnowledgeDeleteResult(result.success(), message);
    }

    /**
     * 更新知识库中的文档
     */
    @Tool(name = "knowledge_update_document",
          description = "更新知识库中的文档信息。当用户要求修改知识库中文档的名称时使用。参数documentId为文档ID，newName为新的文档名称。")
    @ToolInvocationLog("knowledge_update_document")
    public KnowledgeUpdateResult updateKnowledgeDocument(String documentId, String newName) {
        if (documentId == null || documentId.isBlank()) {
            return new KnowledgeUpdateResult(false, "文档ID不能为空");
        }

        if (newName == null || newName.isBlank()) {
            return new KnowledgeUpdateResult(false, "文档名称不能为空");
        }

        RagFlowClient.UpdateResult result = ragFlowClient.updateDocument(documentId, newName);

        String message = result.success() ? result.message() : "更新失败: " + result.message();

        return new KnowledgeUpdateResult(result.success(), message);
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

    /**
     * 知识库上传结果
     */
    public record KnowledgeUploadResult(boolean success, String documentId, String message) {}

    /**
     * 知识库文档列表结果
     */
    public record KnowledgeDocumentListResult(boolean success, List<DocumentInfoItem> documents, String message) {}

    /**
     * 文档信息条目
     */
    public record DocumentInfoItem(String id, String name, String status, Long size, String chunkMethod) {}

    /**
     * 知识库删除结果
     */
    public record KnowledgeDeleteResult(boolean success, String message) {}

    /**
     * 知识库更新结果
     */
    public record KnowledgeUpdateResult(boolean success, String message) {}
}
