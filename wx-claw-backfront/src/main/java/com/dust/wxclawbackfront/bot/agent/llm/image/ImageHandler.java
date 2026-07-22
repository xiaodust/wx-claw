package com.dust.wxclawbackfront.bot.agent.llm.image;

import com.dust.wxclawbackfront.bot.agent.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.bot.agent.tools.time.TimeTools;
import com.dust.wxclawbackfront.bot.agent.tools.weather.WeatherTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ImageHandler {

    private final ChatClient chatClient;
    private final String imageModel;
    private final String defaultModel;
    private final String prompt;
    private final String thinkingType;
    private final int maxTokens;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final TimeTools timeTools;
    private final WeatherTools weatherTools;
    private final AiToolInvocationStore toolInvocationStore;

    public ImageHandler(ChatClient.Builder chatClientBuilder,
                        ObjectMapper objectMapper,
                        TimeTools timeTools,
                        WeatherTools weatherTools,
                        AiToolInvocationStore toolInvocationStore,
                        @Value("${spring.ai.openai.image.model:}") String imageModel,
                        @Value("${spring.ai.openai.chat.model:}") String defaultModel,
                        @Value("${wxclaw.ai.image.prompt:请用中文描述这张图片的内容，尽量提取关键信息与可用于对话的细节。}") String prompt,
                        @Value("${wxclaw.ai.thinking.type:disabled}") String thinkingType,
                        @Value("${wxclaw.ai.image.max-tokens:512}") int maxTokens,
                        @Value("${wxclaw.ai.image.timeout:PT35S}") Duration timeout) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.imageModel = imageModel;
        this.defaultModel = defaultModel;
        this.prompt = prompt;
        this.thinkingType = thinkingType;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.timeTools = timeTools;
        this.weatherTools = weatherTools;
        this.toolInvocationStore = toolInvocationStore;
    }

    public ImageUnderstandingResult understandByUrl(String imageUrl) {
        return understandByUrl(imageUrl, null);
    }

    public ImageUnderstandingResult understandByUrl(String imageUrl, String userText) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl is blank");
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException e) {
            // data: URL 包含特殊字符，使用 3 参数构造器创建 opaque URI
            try {
                uri = new URI("data", imageUrl.trim().substring(5), null);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("无效的图片URL: " + ex.getMessage());
            }
        }
        String modelToUse = (imageModel == null || imageModel.isBlank()) ? defaultModel : imageModel;
        if (modelToUse == null || modelToUse.isBlank()) {
            throw new IllegalStateException("image model is blank");
        }

        String promptToUse;
        if (userText == null || userText.isBlank()) {
            promptToUse = prompt;
        } else {
            promptToUse = "请用中文先描述图片内容，然后结合用户问题给出关键信息。\n用户问题：\n" + userText.trim();
        }

        MimeType mimeType = guessMimeTypeByUrl(imageUrl);
        Media media = new Media(mimeType, uri);

        UserMessage userMessage = UserMessage.builder()
                .text(promptToUse)
                .media(media)
                .build();

        String requestJson = buildImageRequestJson(modelToUse, imageUrl, promptToUse, thinkingType);
        try {
            if (toolInvocationStore != null) {
                toolInvocationStore.reset();
            }
            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelToUse);
            if (thinkingType != null && !thinkingType.isBlank()) {
                optionsBuilder = optionsBuilder.extraBody(Map.of("thinking", Map.of("type", thinkingType.trim())));
            }
            if (maxTokens > 0) {
                optionsBuilder = optionsBuilder.maxTokens(maxTokens);
            }
            if (timeout != null) {
                optionsBuilder = optionsBuilder.timeout(timeout);
            }
            long start = System.currentTimeMillis();
            String description = chatClient.prompt()
                    .tools(timeTools, weatherTools)
                    .options(optionsBuilder)
                    .messages(userMessage)
                    .call()
                    .content();
            long elapsed = System.currentTimeMillis() - start;
            log.info("图片理解完成, 耗时={}ms, model={}", elapsed, modelToUse);
            return new ImageUnderstandingResult(modelToUse, requestJson, description, null);
        } catch (Exception ex) {
            return new ImageUnderstandingResult(modelToUse, requestJson, null, ex.getMessage());
        }
    }

    private String buildImageRequestJson(String model, String imageUrl, String prompt, String thinkingType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            if (thinkingType != null && !thinkingType.isBlank()) {
                payload.put("thinking", Map.of("type", thinkingType.trim()));
            }

            String imageUrlForTrace = TextSanitizer.summarizeDataUrl(imageUrl);
            Map<String, Object> imageItem = new LinkedHashMap<>();
            imageItem.put("type", "input_image");
            imageItem.put("image_url", imageUrlForTrace);

            Map<String, Object> textItem = new LinkedHashMap<>();
            textItem.put("type", "input_text");
            textItem.put("text", prompt);

            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("role", "user");
            inputItem.put("content", List.of(imageItem, textItem));

            payload.put("input", List.of(inputItem));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private static MimeType guessMimeTypeByUrl(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.startsWith("data:image/png")) {
            return new MimeType("image", "png");
        }
        if (lower.startsWith("data:image/webp")) {
            return new MimeType("image", "webp");
        }
        if (lower.startsWith("data:image/gif")) {
            return new MimeType("image", "gif");
        }
        if (lower.startsWith("data:image/jpeg") || lower.startsWith("data:image/jpg")) {
            return new MimeType("image", "jpeg");
        }
        if (lower.contains(".png")) {
            return new MimeType("image", "png");
        }
        if (lower.contains(".webp")) {
            return new MimeType("image", "webp");
        }
        if (lower.contains(".gif")) {
            return new MimeType("image", "gif");
        }
        return new MimeType("image", "jpeg");
    }
}
