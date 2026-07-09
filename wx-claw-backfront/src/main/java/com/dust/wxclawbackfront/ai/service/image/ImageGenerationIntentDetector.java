package com.dust.wxclawbackfront.ai.service.image;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ImageGenerationIntentDetector {

    private static final Pattern IMAGE_GENERATION_PATTERN = Pattern.compile(
            "(画(一张|一个|幅)?(图片|图像|海报|头像|插画|壁纸|封面)|生成(一张|一个|幅)?(图片|图像|海报|头像|插画|壁纸|封面)|给我画|帮我画|帮我生成|给我生成|出一张图|出图|绘制(一张|一个|幅)?(图片|图像)?)",
            Pattern.CASE_INSENSITIVE);

    public boolean isImageGenerationIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return IMAGE_GENERATION_PATTERN.matcher(text.trim()).find();
    }
}
