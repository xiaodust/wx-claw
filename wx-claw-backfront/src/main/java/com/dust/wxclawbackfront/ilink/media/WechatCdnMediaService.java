package com.dust.wxclawbackfront.ilink.media;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Slf4j
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

    /**
     * 下载视频
     */
    public ResolvedVideo resolveVideo(ILinkClient client, MessageItem messageItem) {
        if (messageItem == null || messageItem.getVideo_item() == null) {
            return null;
        }
        if (client == null) {
            return null;
        }
        try {
            byte[] videoBytes = client.downloadVideoFromMessageItem(messageItem);
            if (videoBytes == null || videoBytes.length == 0) {
                log.warn("视频下载失败");
                return null;
            }
            log.info("视频下载成功, size={}", videoBytes.length);
            return new ResolvedVideo(videoBytes, messageItem.getVideo_item().getVideo_size());
        } catch (Exception ex) {
            log.error("下载视频异常: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 下载文件
     */
    public ResolvedFile resolveFile(ILinkClient client, MessageItem messageItem) {
        if (messageItem == null) {
            return null;
        }
        FileItem fileItem = messageItem.getFile_item();
        if (fileItem == null) {
            return null;
        }

        String fileName = fileItem.getFile_name();
        String fileSize = fileItem.getLen();

        // 使用SDK下载文件
        byte[] fileBytes = downloadFileWithSdk(client, messageItem);
        if (fileBytes == null || fileBytes.length == 0) {
            log.warn("文件下载失败: fileName={}", fileName);
            return null;
        }

        log.info("文件下载成功: fileName={}, size={}", fileName, fileBytes.length);
        return new ResolvedFile(fileName, fileSize, fileBytes);
    }

    private byte[] downloadFileWithSdk(ILinkClient client, MessageItem messageItem) {
        if (client == null || messageItem == null) {
            return null;
        }
        try {
            return client.downloadFileFromMessageItem(messageItem);
        } catch (Exception ex) {
            log.error("下载文件异常: {}", ex.getMessage());
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

    public record ResolvedFile(String fileName, String fileSize, byte[] fileBytes) {
    }

    public record ResolvedVideo(byte[] videoBytes, Long videoSize) {
    }
}
