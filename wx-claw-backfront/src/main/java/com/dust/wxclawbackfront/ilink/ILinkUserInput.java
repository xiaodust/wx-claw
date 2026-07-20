package com.dust.wxclawbackfront.ilink;

import lombok.Getter;

@Getter
public final class ILinkUserInput {

    private final String messageItemType;
    private final String displayText;
    private final String promptText;
    private final String persistText;
    private final String imageUrl;
    private final String imageModel;
    private final String imageDescription;
    private final String imageLlmRequestJson;
    private final String fileUrl;
    private final String fileName;
    private final String fileSize;
    private final byte[] fileBytes;
    private final String error;

    private ILinkUserInput(String messageItemType,
                           String displayText,
                           String promptText,
                           String persistText,
                           String imageUrl,
                           String imageModel,
                           String imageDescription,
                           String imageLlmRequestJson,
                           String fileUrl,
                           String fileName,
                           String fileSize,
                           byte[] fileBytes,
                           String error) {
        this.messageItemType = messageItemType;
        this.displayText = displayText;
        this.promptText = promptText;
        this.persistText = persistText;
        this.imageUrl = imageUrl;
        this.imageModel = imageModel;
        this.imageDescription = imageDescription;
        this.imageLlmRequestJson = imageLlmRequestJson;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileBytes = fileBytes;
        this.error = error;
    }

    public static ILinkUserInput text(String text) {
        return new ILinkUserInput("TEXT", text, text, text, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 创建包含引用消息的文本输入
     */
    public static ILinkUserInput textWithQuote(String text, String quotedText) {
        String display = text;
        String persist = text;
        String prompt;
        if (quotedText != null && !quotedText.isBlank()) {
            prompt = "用户引用了一段消息：「" + quotedText.trim() + "」\n用户说：" + text;
        } else {
            prompt = text;
        }
        return new ILinkUserInput("TEXT", display, prompt, persist, null, null, null, null, null, null, null, null, null);
    }

    public static ILinkUserInput image(String url, String model, String description, String imageLlmRequestJson, String error) {
        String display = "[IMAGE]";
        String persist;
        if (description != null && !description.isBlank()) {
            persist = "[IMAGE_DESC] " + truncate(description.trim(), 800);
        } else if (url != null && !url.isBlank()) {
            persist = "[IMAGE_URL] " + truncate(url.trim(), 500);
        } else {
            persist = "[IMAGE]";
        }

        String prompt;
        if (error != null && !error.isBlank()) {
            prompt = "用户发送了一张图片，但获取图片失败，错误信息如下：\n" + error + "\n请给用户一个简短提示，并引导用户重新发送。";
        } else if (description != null && !description.isBlank()) {
            prompt = "用户发送了一张图片。图片内容描述如下：\n" + description + "\n请用中文基于上述描述回复用户。";
        } else if (url != null && !url.isBlank()) {
            prompt = "用户发送了一张图片（URL: " + url + "），等待用户说明意图后再处理。";
        } else {
            prompt = "用户发送了一张图片，但未获得图片内容描述。请给用户一个简短提示，并引导用户重新发送。";
        }

        return new ILinkUserInput("IMAGE", display, prompt, persist, url, model, description, imageLlmRequestJson, null, null, null, null, error);
    }

    public static ILinkUserInput file(String url, String fileName, String fileSize, byte[] fileBytes) {
        String display = "[FILE:" + (fileName != null ? fileName : "unknown") + "]";
        String persist = "[FILE] " + fileName + (fileSize != null ? " (" + fileSize + " bytes)" : "");
        String prompt = "用户发送了一个文件：" + fileName + (fileSize != null ? "，大小：" + fileSize + "字节" : "") 
                + "。请用中文告知用户文件已收到。";
        return new ILinkUserInput("FILE", display, prompt, persist, null, null, null, null, url, fileName, fileSize, fileBytes, null);
    }

    public static ILinkUserInput unsupported(String type) {
        String display = "[UNSUPPORTED:" + type + "]";
        String prompt = "用户发送了非文本消息类型：" + type + "。请用中文礼貌回复：目前仅支持文字、图片和文件，其他类型暂不支持。";
        return new ILinkUserInput(type, display, prompt, display, null, null, null, null, null, null, null, null, null);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
