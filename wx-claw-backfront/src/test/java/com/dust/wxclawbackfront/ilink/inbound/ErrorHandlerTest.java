package com.dust.wxclawbackfront.ilink.inbound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorHandlerTest {

    @Test
    void ttsNotGrantedGivesActionableHint() {
        String msg = ErrorHandler.buildFallbackMessage(
                "语音合成失败: 豆包语音模型服务未开通/未授权（[resource_id=volc.service_type.10074] requested resource not granted）");

        assertEquals("语音功能暂未开通（豆包语音服务未授权），请到火山引擎豆包语音控制台开通语音合成服务后重试。", msg);
    }

    @Test
    void ttsInvalidKeyGivesSpecificHint() {
        String msg = ErrorHandler.buildFallbackMessage(
                "语音合成失败: API Key 无效或未配置，请在豆包语音控制台核对 API Key");

        assertEquals("语音功能配置的 API Key 无效，请在设置页核对语音合成 Key。", msg);
    }

    @Test
    void ttsGenericFailureFallsBack() {
        String msg = ErrorHandler.buildFallbackMessage("语音合成失败: TTS 请求失败，HTTP 500，服务端返回: boom");

        assertEquals("语音生成失败，请稍后再试。", msg);
    }

    @Test
    void imageFailureHasDedicatedMessage() {
        assertEquals("图片生成失败，请稍后再试。",
                ErrorHandler.buildFallbackMessage("生图失败: upstream timeout"));
    }

    @Test
    void unknownFailureStaysGeneric() {
        assertEquals("处理失败，请稍后再试。",
                ErrorHandler.buildFallbackMessage("some unrelated error"));
    }
}
