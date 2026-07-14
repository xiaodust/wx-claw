package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.image.ImageHandler;
import com.dust.wxclawbackfront.ai.image.ImageUnderstandingResult;
import com.dust.wxclawbackfront.ilnk.media.WechatCdnMediaService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
@Slf4j
@Component
public class ILinkUserInputExtractor {

    private static final int MESSAGE_ITEM_TYPE_TEXT = 1;
    private static final int MESSAGE_ITEM_TYPE_IMAGE = 2;
    private static final int MESSAGE_ITEM_TYPE_VOICE = 3;
    private static final int MESSAGE_ITEM_TYPE_FILE = 4;

    private final ImageHandler imageHandler;
    private final WechatCdnMediaService cdnMediaService;

    public ILinkUserInputExtractor(ImageHandler imageHandler, WechatCdnMediaService cdnMediaService) {
        this.imageHandler = imageHandler;
        this.cdnMediaService = cdnMediaService;
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
                
                // 下载文件
                WechatCdnMediaService.ResolvedFile resolvedFile = cdnMediaService.resolveFile(client, item);
                byte[] fileBytes = resolvedFile != null ? resolvedFile.fileBytes() : null;
                
                if (fileBytes == null || fileBytes.length == 0) {
                    log.warn("文件下载失败: fileName={}", fileName);
                    return ILinkUserInput.file(null, fileName, fileSize, null);
                }
                
                log.info("文件下载成功: fileName={}, actualSize={}", fileName, fileBytes.length);
                return ILinkUserInput.file(null, fileName, fileSize, fileBytes);
            }
        }

        String text = extractText(msg);
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
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

    private static String getMessageTypeName(int type) {
        return switch (type) {
            case MESSAGE_ITEM_TYPE_TEXT -> "TEXT";
            case MESSAGE_ITEM_TYPE_IMAGE -> "IMAGE";
            case MESSAGE_ITEM_TYPE_VOICE -> "VOICE";
            case MESSAGE_ITEM_TYPE_FILE -> "FILE";
            default -> "TYPE_" + type;
        };
    }
}

