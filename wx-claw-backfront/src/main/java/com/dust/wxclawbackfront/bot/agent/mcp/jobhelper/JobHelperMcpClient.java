package com.dust.wxclawbackfront.bot.agent.mcp.jobhelper;

import com.dust.wxclawbackfront.bot.agent.mcp.jobhelper.dto.JobHelperDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHelperMcpClient {
    private static final String SERVER_NAME = "job-helper";

    private final List<McpSyncClient> clients;
    private final ObjectMapper objectMapper;
    private volatile McpSyncClient resolvedClient;

    public JobHelperMcpClient(List<McpSyncClient> clients, ObjectMapper objectMapper) {
        this.clients = clients;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public JobHelperDtos.JobSearchResponse search(JobHelperDtos.JobFilters filters, int page, int pageSize) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("filters", objectMapper.convertValue(filters, Map.class));
        arguments.put("page", page);
        arguments.put("pageSize", pageSize);
        return call("search_jobs", arguments, JobHelperDtos.JobSearchResponse.class);
    }

    public JobHelperDtos.StoredResume saveResume(UserIdentity identity, ResumeFile resume) {
        return call("save_resume", Map.of(
                "tenantId", identity.tenantId(),
                "externalUserId", identity.externalUserId(),
                "fileName", resume.fileName(),
                "fileBase64", Base64.getEncoder().encodeToString(resume.fileBytes())),
                JobHelperDtos.StoredResume.class);
    }

    public JobHelperDtos.CurrentResume currentResume(UserIdentity identity) {
        return call("get_current_resume", identity(identity), JobHelperDtos.CurrentResume.class);
    }

    public boolean deleteResume(UserIdentity identity) {
        return call("delete_resume", identity(identity), JobHelperDtos.DeleteResumeResult.class).deleted();
    }

    public JobHelperDtos.ResumeScoreResponse score(UserIdentity identity, String jobDescription) {
        Map<String, Object> arguments = new LinkedHashMap<>(identity(identity));
        if (jobDescription != null && !jobDescription.isBlank()) arguments.put("jobDescription", jobDescription);
        arguments.put("forceRefresh", false);
        return call("score_resume", arguments, JobHelperDtos.ResumeScoreResponse.class);
    }

    public JobHelperDtos.JobRecommendationResponse recommend(UserIdentity identity,
                                                              JobHelperDtos.JobFilters filters) {
        Map<String, Object> arguments = new LinkedHashMap<>(identity(identity));
        arguments.put("filters", objectMapper.convertValue(filters, Map.class));
        arguments.put("forceRefresh", false);
        return call("recommend_jobs", arguments, JobHelperDtos.JobRecommendationResponse.class);
    }

    public byte[] readResume(String resourceUri) {
        try {
            McpSchema.ReadResourceResult result = client().readResource(new McpSchema.ReadResourceRequest(resourceUri));
            if (result.contents().isEmpty()
                    || !(result.contents().getFirst() instanceof McpSchema.BlobResourceContents blob)) {
                throw failure("MCP resource did not return a PDF blob", null);
            }
            return Base64.getDecoder().decode(blob.blob());
        } catch (JobHelperMcpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("Unable to read resume through MCP", exception);
        }
    }

    private Map<String, Object> identity(UserIdentity identity) {
        return Map.of("tenantId", identity.tenantId(), "externalUserId", identity.externalUserId());
    }

    private <T> T call(String toolName, Map<String, Object> arguments, Class<T> responseType) {
        try {
            McpSchema.CallToolResult result = client().callTool(new McpSchema.CallToolRequest(toolName, arguments));
            if (Boolean.TRUE.equals(result.isError())) throw failure(toolError(result), null);
            if (result.structuredContent() != null) {
                return objectMapper.convertValue(result.structuredContent(), responseType);
            }
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent text && text.text() != null) {
                    return objectMapper.readValue(text.text(), responseType);
                }
            }
            throw failure("MCP tool returned no content: " + toolName, null);
        } catch (JobHelperMcpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("MCP tool call failed: " + toolName, exception);
        }
    }

    private synchronized McpSyncClient client() {
        if (resolvedClient != null && resolvedClient.isInitialized()) return resolvedClient;
        Throwable lastFailure = null;
        for (McpSyncClient candidate : clients) {
            try {
                if (!candidate.isInitialized()) candidate.initialize();
                if (candidate.getServerInfo() != null && SERVER_NAME.equals(candidate.getServerInfo().name())) {
                    resolvedClient = candidate;
                    return candidate;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }
        throw failure("No initialized Job Helper MCP server is configured", lastFailure);
    }

    private String toolError(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst().orElse("Job Helper MCP tool returned an error");
    }

    private JobHelperMcpException failure(String message, Throwable cause) {
        return new JobHelperMcpException(0, "JOB_HELPER_MCP_FAILED", message, null, null, cause);
    }

    public record UserIdentity(String tenantId, String externalUserId) { }

    public record ResumeFile(String fileName, byte[] fileBytes, String sha256) {
        public ResumeFile {
            fileBytes = fileBytes.clone();
        }

        @Override
        public byte[] fileBytes() {
            return fileBytes.clone();
        }
    }
}
