package com.dust.wxclawbackfront.ai.image;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ImageGenerationIntentDetector {

    // 只匹配用户消息开头的命令式表达，避免匹配 AI 回复中的"给你画"等
    // 要求：以动词或"帮/给/为我"开头，且前面没有其他文字
    private static final Pattern IMAGE_GENERATION_PATTERN = Pattern.compile(
            "^(画|重画|重新画|生成|出图|绘制|绘图|帮我画|帮我重画|帮我生成|帮我绘|给我画|给我重画|给我生成|给我绘|为我画|为我重画|为我生成|为我绘|画一张|画一个|画一幅|生成一张|生成一个|生成一幅|出一张图|画个|画幅|再来一张|再画|再生成)",
            Pattern.CASE_INSENSITIVE);

    // 包含关键词但不在开头的情况（如"画个头像"、"生成图片"）
    private static final Pattern IMAGE_KEYWORD_PATTERN = Pattern.compile(
            "(画|生成|绘制).*(图片|图像|海报|头像|插画|壁纸|封面|图)$",
            Pattern.CASE_INSENSITIVE);

    public boolean isImageGenerationIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        
        // 1. 匹配开头的命令式表达
        if (IMAGE_GENERATION_PATTERN.matcher(trimmed).find()) {
            return true;
        }
        
        // 2. 匹配包含关键词的短句（如"画个头像"）
        if (trimmed.length() < 20 && IMAGE_KEYWORD_PATTERN.matcher(trimmed).find()) {
            return true;
        }
        
        return false;
    }
}
