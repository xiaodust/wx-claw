package com.dust.wxclawbackfront.ilink;

import com.dust.wxclawbackfront.bot.agent.llm.chat.file.FileContentExtractor;
import com.dust.wxclawbackfront.bot.agent.llm.image.ImageHandler;
import com.dust.wxclawbackfront.bot.agent.llm.image.ImageUnderstandingResult;
import com.dust.wxclawbackfront.bot.agent.llm.video.VideoHandler;
import com.dust.wxclawbackfront.bot.agent.llm.video.VideoUnderstandingResult;
import com.dust.wxclawbackfront.ilink.media.WechatCdnMediaService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
@Slf4j
@Component
public class ILinkUserInputExtractor {

    private static final int MESSAGE_ITEM_TYPE_TEXT = 1;
    private static final int MESSAGE_ITEM_TYPE_IMAGE = 2;
    private static final int MESSAGE_ITEM_TYPE_VOICE = 3;
    private static final int MESSAGE_ITEM_TYPE_FILE = 4;
    private static final int MESSAGE_ITEM_TYPE_VIDEO = 5;

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".webm", ".m4v", ".3gp"
    );

    private final ImageHandler imageHandler;
    private final VideoHandler videoHandler;
    private final WechatCdnMediaService cdnMediaService;
    private final FileContentExtractor fileContentExtractor;

    public ILinkUserInputExtractor(ImageHandler imageHandler, VideoHandler videoHandler,
                                     WechatCdnMediaService cdnMediaService,
                                     FileContentExtractor fileContentExtractor) {
        this.imageHandler = imageHandler;
        this.videoHandler = videoHandler;
        this.cdnMediaService = cdnMediaService;
        this.fileContentExtractor = fileContentExtractor;
    }

    public ILinkUserInput extract(ILinkClient client, WeixinMessage msg) {
        List<MessageItem> items = msg == null ? null : msg.getItem_list();
        if (items == null || items.isEmpty()) {
            return null;
        }

        for (MessageItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == MESSAGE_ITEM_TYPE_IMAGE && item.getImage_item() != null) {
                WechatCdnMediaService.ResolvedImage resolved = cdnMediaService.resolveImage(client, item);
                String url = resolved == null ? null : resolved.accessibleUrl();
                String userText = extractText(msg);
                String description = null;
                String model = null;
                String requestJson = null;
                String error = null;
                if (url != null && !url.isBlank()) {
                    ImageUnderstandingResult result = imageHandler.understandByUrl(url, userText);
                    if (result != null) {
                        description = result.getDescription();
                        model = result.getModel();
                        requestJson = result.getRequestJson();
                        error = result.getErrorMsg();
                    }
                }
                if (error != null && !error.isBlank()) {
                    return ILinkUserInput.image(url, model, description, requestJson, "图片理解失败: " + error);
                }
                return ILinkUserInput.image(url, model, description, requestJson, null);
            }
            // 处理视频消息（类型5）
            if (item.getType() == MESSAGE_ITEM_TYPE_VIDEO && item.getVideo_item() != null) {
                log.info("收到视频消息: type=VIDEO, videoSize={}", item.getVideo_item().getVideo_size());
                return handleVideoMessage(client, item);
            }
        }

        // 处理文件消息
        for (MessageItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.getType() == MESSAGE_ITEM_TYPE_FILE && item.getFile_item() != null) {
                FileItem fileItem = item.getFile_item();
                String fileName = fileItem.getFile_name();
                String fileSize = fileItem.getLen();
                
                log.info("收到文件消息: fileName={}, fileSize={}", fileName, fileSize);
                
                // 判断是否为视频文件
                if (isVideoFile(fileName)) {
                    return handleVideoFile(client, item, fileName, fileSize);
                }

                // 普通文件处理：下载并提取内容
                WechatCdnMediaService.ResolvedFile resolvedFile = cdnMediaService.resolveFile(client, item);
                byte[] fileBytes = resolvedFile != null ? resolvedFile.fileBytes() : null;
                
                if (fileBytes == null || fileBytes.length == 0) {
                    log.warn("文件下载失败: fileName={}", fileName);
                    return ILinkUserInput.file(null, fileName, fileSize, null, null);
                }
                
                log.info("文件下载成功: fileName={}, actualSize={}", fileName, fileBytes.length);

                // 提取文件内容
                String fileContent = null;
                FileContentExtractor.FileExtractResult extractResult = fileContentExtractor.extract(fileBytes, fileName);
                if (extractResult.isSuccess()) {
                    fileContent = extractResult.content();
                    log.info("文件内容提取成功: fileName={}, contentLength={}", fileName, fileContent.length());
                } else {
                    log.warn("文件内容提取失败: fileName={}, error={}", fileName, extractResult.error());
                }

                return ILinkUserInput.file(null, fileName, fileSize, fileBytes, fileContent);
            }
        }

        String text = extractText(msg);
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                // 提取引用消息文本
                String refText = extractRefText(msg);
                if (refText != null && !refText.isBlank()) {
                    return ILinkUserInput.textWithQuote(trimmed, refText);
                }
                return ILinkUserInput.text(trimmed);
            }
        }

        for (MessageItem item : items) {
            if (item != null && item.getType() != 0) {
                return ILinkUserInput.unsupported(getMessageTypeName(item.getType()));
            }
        }
        return null;
    }

    public String extractText(WeixinMessage msg) {
        List<MessageItem> items = msg == null ? null : msg.getItem_list();
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (MessageItem item : items) {
            if (item == null) {
                continue;
            }
            
            if (item.getType() == MESSAGE_ITEM_TYPE_TEXT && item.getText_item() != null) {
                String text = item.getText_item().getText();
                if (text != null && !text.isBlank()) {
                    parts.add(text.trim());
                }
            } else if (item.getType() == MESSAGE_ITEM_TYPE_VOICE && item.getVoice_item() != null) {
                // 微信服务端已经做了语音识别，text 字段包含识别后的文字
                String voiceText = item.getVoice_item().getText();
                if (voiceText != null && !voiceText.isBlank()) {
                    parts.add(voiceText.trim());
                    log.debug("语音消息已识别为文字: {}", voiceText);
                } else {
                    log.warn("语音消息未包含识别文字，可能识别失败或为空语音");
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n", parts);
    }

    /**
     * 提取引用消息文本
     */
    public String extractRefText(WeixinMessage msg) {
        List<MessageItem> items = msg == null ? null : msg.getItem_list();
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        // 遍历消息项，查找包含引用消息的项
        for (MessageItem item : items) {
            if (item != null && item.hasRefMessage()) {
                String refText = item.getRefMessageText();
                if (refText != null && !refText.isBlank()) {
                    return refText.trim();
                }
            }
        }
        
        return null;
    }

    private static String getMessageTypeName(int type) {
        return switch (type) {
            case MESSAGE_ITEM_TYPE_TEXT -> "TEXT";
            case MESSAGE_ITEM_TYPE_IMAGE -> "IMAGE";
            case MESSAGE_ITEM_TYPE_VOICE -> "VOICE";
            case MESSAGE_ITEM_TYPE_FILE -> "FILE";
            case MESSAGE_ITEM_TYPE_VIDEO -> "VIDEO";
            default -> "TYPE_" + type;
        };
    }

    private static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private ILinkUserInput handleVideoFile(ILinkClient client, MessageItem item, String fileName, String fileSize) {
        log.info("检测到视频文件: fileName={}, 进行视频理解", fileName);

        if (!videoHandler.isEnabled()) {
            log.warn("视频理解未启用或未配置，跳过视频理解");
            return ILinkUserInput.video(null, null, null, null, "视频理解功能未配置");
        }

        // 下载视频文件
        WechatCdnMediaService.ResolvedFile resolvedFile = cdnMediaService.resolveFile(client, item);
        byte[] videoBytes = resolvedFile != null ? resolvedFile.fileBytes() : null;

        if (videoBytes == null || videoBytes.length == 0) {
            log.warn("视频文件下载失败: fileName={}", fileName);
            return ILinkUserInput.video(null, null, null, null, "视频文件下载失败");
        }

        log.info("视频文件下载成功: fileName={}, size={}", fileName, videoBytes.length);

        // 使用 base64 进行视频理解
        String base64 = java.util.Base64.getEncoder().encodeToString(videoBytes);
        String mimeType = guessVideoMimeType(fileName);
        VideoUnderstandingResult result = videoHandler.understandByBase64(base64, mimeType);

        String description = result.getDescription();
        String model = result.getModel();
        String requestJson = result.getRequestJson();
        String error = result.getErrorMsg();

        if (error != null && !error.isBlank()) {
            return ILinkUserInput.video(null, model, description, requestJson, "视频理解失败: " + error);
        }
        return ILinkUserInput.video(null, model, description, requestJson, null);
    }

    /**
     * 处理视频消息（类型5，SDK原生支持）
     */
    private ILinkUserInput handleVideoMessage(ILinkClient client, MessageItem item) {
        if (!videoHandler.isEnabled()) {
            log.warn("视频理解未启用或未配置，跳过视频理解");
            return ILinkUserInput.video(null, null, null, null, "视频理解功能未配置");
        }

        WechatCdnMediaService.ResolvedVideo resolved = cdnMediaService.resolveVideo(client, item);
        if (resolved == null || resolved.videoBytes() == null || resolved.videoBytes().length == 0) {
            log.warn("视频下载失败");
            return ILinkUserInput.video(null, null, null, null, "视频下载失败");
        }

        log.info("视频下载成功, size={}", resolved.videoBytes().length);

        String base64 = java.util.Base64.getEncoder().encodeToString(resolved.videoBytes());
        VideoUnderstandingResult result = videoHandler.understandByBase64(base64, "video/mp4");

        String description = result.getDescription();
        String model = result.getModel();
        String requestJson = result.getRequestJson();
        String error = result.getErrorMsg();

        if (error != null && !error.isBlank()) {
            return ILinkUserInput.video(null, model, description, requestJson, "视频理解失败: " + error);
        }
        return ILinkUserInput.video(null, model, description, requestJson, null);
    }

    private static String guessVideoMimeType(String fileName) {
        if (fileName == null) return "video/mp4";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        return "video/mp4";
    }
}

