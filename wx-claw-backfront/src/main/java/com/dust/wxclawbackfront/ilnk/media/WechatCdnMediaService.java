package com.dust.wxclawbackfront.ilnk.media;

import com.openilink.model.CDNMedia;
import com.openilink.model.ImageItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class WechatCdnMediaService {

    private final WechatCdnClient cdnClient;
    private final TempMediaStore mediaStore;

    public WechatCdnMediaService(TempMediaStore mediaStore,
                                 @Value("${wxclaw.wechat.cdn.base-url:https://novac2c.cdn.weixin.qq.com/c2c}") String cdnBaseUrl) {
        this.cdnClient = new WechatCdnClient(cdnBaseUrl);
        this.mediaStore = mediaStore;
    }

    public ResolvedImage resolveImage(ImageItem imageItem) {
        if (imageItem == null) {
            return null;
        }
        if (imageItem.getUrl() != null && !imageItem.getUrl().isBlank()) {
            return ResolvedImage.direct(imageItem.getUrl().trim());
        }

        CDNMedia media = imageItem.getMedia();
        if (media == null || media.getEncryptQueryParam() == null || media.getEncryptQueryParam().isBlank()) {
            return null;
        }
        String encryptQueryParam = media.getEncryptQueryParam().trim();
        String aesKey = null;
        if (media.getAesKey() != null && !media.getAesKey().isBlank()) {
            aesKey = media.getAesKey();
        } else if (imageItem.getAesKey() != null && !imageItem.getAesKey().isBlank()) {
            aesKey = imageItem.getAesKey();
        }
        if (aesKey == null || aesKey.isBlank()) {
            return null;
        }

        byte[] encrypted = cdnClient.downloadEncrypted(encryptQueryParam);
        byte[] plain = WechatCdnCrypto.decryptAes128EcbPkcs7(encrypted, aesKey);
        ImageInfo info = guessImageInfo(plain);
        TempMediaStore.MediaRef ref = mediaStore.put(plain, info.contentType(), info.ext());
        String dataUrl = "data:" + info.contentType() + ";base64," + Base64.getEncoder().encodeToString(plain);
        return ResolvedImage.decrypted(dataUrl, encryptQueryParam, ref.localPath(), info.contentType());
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

    public record ResolvedImage(String accessibleUrl, String encryptQueryParam, boolean decrypted, String localPath, String contentType) {
        static ResolvedImage direct(String url) {
            return new ResolvedImage(url, null, false, null, null);
        }

        static ResolvedImage decrypted(String relativeUrl, String encryptQueryParam, String localPath, String contentType) {
            return new ResolvedImage(relativeUrl, encryptQueryParam, true, localPath, contentType);
        }
    }
}
