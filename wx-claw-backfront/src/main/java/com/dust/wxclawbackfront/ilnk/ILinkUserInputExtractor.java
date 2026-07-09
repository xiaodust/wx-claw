package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.service.image.ImageHandler;
import com.dust.wxclawbackfront.ai.service.image.ImageUnderstandingResult;
import com.dust.wxclawbackfront.ilnk.media.WechatCdnMediaService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ILinkUserInputExtractor {

    private static final int MESSAGE_ITEM_TYPE_TEXT = 1;
    private static final int MESSAGE_ITEM_TYPE_IMAGE = 2;

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
            if (item == null || item.getType() != MESSAGE_ITEM_TYPE_TEXT || item.getText_item() == null) {
                continue;
            }
            String text = item.getText_item().getText();
            if (text != null && !text.isBlank()) {
                parts.add(text.trim());
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
            default -> "TYPE_" + type;
        };
    }
}

