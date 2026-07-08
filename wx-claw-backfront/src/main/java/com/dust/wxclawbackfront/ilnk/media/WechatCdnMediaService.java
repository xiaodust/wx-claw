package com.dust.wxclawbackfront.ilnk.media;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class WechatCdnMediaService {

    public ResolvedImage resolveImage(ILinkClient client, MessageItem messageItem) {
        if (messageItem == null) {
            return null;
        }
        ImageItem imageItem = messageItem.getImage_item();
        if (imageItem == null) {
            return null;
        }
        if (imageItem.getUrl() != null && !imageItem.getUrl().isBlank()) {
            return ResolvedImage.direct(imageItem.getUrl().trim());
        }

        byte[] plain = downloadWithSdk(client, messageItem);
        if (plain == null || plain.length == 0) {
            return null;
        }

        ImageInfo info = guessImageInfo(plain);
        String dataUrl = "data:" + info.contentType() + ";base64," + Base64.getEncoder().encodeToString(plain);
        String encryptQueryParam = imageItem.getMedia() == null ? null : imageItem.getMedia().getEncrypt_query_param();
        return ResolvedImage.decrypted(dataUrl, encryptQueryParam, info.contentType());
    }

    private byte[] downloadWithSdk(ILinkClient client, MessageItem messageItem) {
        if (client == null || messageItem == null) {
            return null;
        }
        try {
            return client.downloadImageThumbFromMessageItem(messageItem);
        } catch (Exception ignored) {
        }
        try {
            return client.downloadImageFromMessageItem(messageItem);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ImageInfo guessImageInfo(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return new ImageInfo("image/jpeg", "jpg");
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return new ImageInfo("image/png", "png");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return new ImageInfo("image/jpeg", "jpg");
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return new ImageInfo("image/gif", "gif");
        }
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return new ImageInfo("image/webp", "webp");
        }
        return new ImageInfo("application/octet-stream", "bin");
    }

    private record ImageInfo(String contentType, String ext) {
    }

    public record ResolvedImage(String accessibleUrl, String encryptQueryParam, boolean decrypted, String contentType) {
        static ResolvedImage direct(String url) {
            return new ResolvedImage(url, null, false, null);
        }

        static ResolvedImage decrypted(String relativeUrl, String encryptQueryParam, String contentType) {
            return new ResolvedImage(relativeUrl, encryptQueryParam, true, contentType);
        }
    }
}
