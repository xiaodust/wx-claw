package com.dust.wxclawbackfront.ilnk;

import com.dust.wxclawbackfront.ai.tools.ImageHandler;
import com.dust.wxclawbackfront.ai.tools.ImageUnderstandingResult;
import com.dust.wxclawbackfront.ilnk.media.WechatCdnMediaService;
import com.openilink.model.MessageItem;
import com.openilink.model.MessageItemType;
import com.openilink.model.WeixinMessage;
import com.openilink.util.MessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ILinkUserInputExtractor {

    private final ImageHandler imageHandler;
    private final WechatCdnMediaService cdnMediaService;
    private final String publicBaseUrl;

    public ILinkUserInputExtractor(ImageHandler imageHandler,
                                   WechatCdnMediaService cdnMediaService,
                                   @Value("${wxclaw.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.imageHandler = imageHandler;
        this.cdnMediaService = cdnMediaService;
        this.publicBaseUrl = publicBaseUrl;
    }

    public ILinkUserInput extract(WeixinMessage msg) {
        List<MessageItem> items = msg == null ? null : msg.getItemList();
        if (items == null || items.isEmpty()) {
            return null;
        }

        for (MessageItem item : items) {
            if (item == null || item.getType() == null) {
                continue;
            }
            if (item.getType() == MessageItemType.IMAGE && item.getImageItem() != null) {
                WechatCdnMediaService.ResolvedImage resolved = cdnMediaService.resolveImage(item.getImageItem());
                String url = resolved == null ? null : resolved.accessibleUrl();
                String localPath = resolved == null ? null : resolved.localPath();
                String userText = MessageHelper.extractText(msg);
                String description = null;
                String model = null;
                String requestJson = null;
                String error = null;
                if (url != null && !url.isBlank()) {
                    ImageUnderstandingResult result = imageHandler.understandByUrl(toAbsoluteUrlIfNeeded(url), userText);
                    if (result != null) {
                        description = result.getDescription();
                        model = result.getModel();
                        requestJson = result.getRequestJson();
                        error = result.getErrorMsg();
                    }
                }
                if (error != null && !error.isBlank()) {
                    return ILinkUserInput.image(url, model, description, requestJson, localPath, "图片理解失败: " + error);
                }
                return ILinkUserInput.image(url, model, description, requestJson, localPath, null);
            }
        }

        String text = MessageHelper.extractText(msg);
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                return ILinkUserInput.text(trimmed);
            }
        }

        MessageItemType type = null;
        for (MessageItem item : items) {
            if (item != null && item.getType() != null && item.getType() != MessageItemType.NONE) {
                type = item.getType();
                break;
            }
        }
        if (type == null) {
            return null;
        }
        return ILinkUserInput.unsupported(type.name());
    }

    private String toAbsoluteUrlIfNeeded(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("data:")) {
            return trimmed;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/") && trimmed.startsWith("/")) {
            return base.substring(0, base.length() - 1) + trimmed;
        }
        if (!base.endsWith("/") && !trimmed.startsWith("/")) {
            return base + "/" + trimmed;
        }
        return base + trimmed;
    }
}
