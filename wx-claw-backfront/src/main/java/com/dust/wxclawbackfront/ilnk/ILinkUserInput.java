package com.dust.wxclawbackfront.ilnk;

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
    private final String imageLocalPath;
    private final String error;

    private ILinkUserInput(String messageItemType,
                           String displayText,
                           String promptText,
                           String persistText,
                           String imageUrl,
                           String imageModel,
                           String imageDescription,
                           String imageLlmRequestJson,
                           String imageLocalPath,
                           String error) {
        this.messageItemType = messageItemType;
        this.displayText = displayText;
        this.promptText = promptText;
        this.persistText = persistText;
        this.imageUrl = imageUrl;
        this.imageModel = imageModel;
        this.imageDescription = imageDescription;
        this.imageLlmRequestJson = imageLlmRequestJson;
        this.imageLocalPath = imageLocalPath;
        this.error = error;
    }

    public static ILinkUserInput text(String text) {
        return new ILinkUserInput("TEXT", text, text, text, null, null, null, null, null, null);
    }

    public static ILinkUserInput image(String url, String model, String description, String imageLlmRequestJson, String imageLocalPath, String error) {
        String display = "[IMAGE]";
        String persist;
        if (description != null && !description.isBlank()) {
            persist = "[IMAGE_DESC] " + truncate(description.trim(), 800);
        } else {
            persist = "[IMAGE]";
        }

        String prompt;
        if (error != null && !error.isBlank()) {
            prompt = "用户发送了一张图片，但图片理解失败，错误信息如下：\n" + error + "\n请给用户一个简短提示，并引导用户重新发送。";
        } else if (description == null || description.isBlank()) {
            prompt = "用户发送了一张图片，但未获得图片内容描述。请给用户一个简短提示，并引导用户重新发送。";
        } else {
            prompt = "用户发送了一张图片。图片内容描述如下：\n" + description + "\n请用中文基于上述描述回复用户。";
        }

        return new ILinkUserInput("IMAGE", display, prompt, persist, url, model, description, imageLlmRequestJson, imageLocalPath, error);
    }

    public static ILinkUserInput unsupported(String type) {
        String display = "[UNSUPPORTED:" + type + "]";
        String prompt = "用户发送了非文本消息类型：" + type + "。请用中文礼貌回复：目前仅支持文字与图片，其他类型暂不支持。";
        return new ILinkUserInput(type, display, prompt, display, null, null, null, null, null, null);
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
