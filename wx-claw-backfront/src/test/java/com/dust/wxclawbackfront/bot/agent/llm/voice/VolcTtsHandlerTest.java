package com.dust.wxclawbackfront.bot.agent.llm.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcTtsHandlerTest {

    private final VolcTtsHandler handler = new VolcTtsHandler(
            new ObjectMapper(), null, null, null, 0, null, null, null,
            null, null, null, null, 0, 0, 0);

    @Test
    void resourceNotGrantedMapsToActionableHint() {
        String err = handler.buildHttpError(403,
                "{\"code\":45000030,\"message\":\"[resource_id=volc.service_type.10074] requested resource not granted\"}");

        assertTrue(err.contains("未开通"));
        assertTrue(err.contains("console.volcengine.com/speech/new/setting/apikeys"));
    }

    @Test
    void http401MapsToInvalidKeyHint() {
        String err = handler.buildHttpError(401, "");

        assertTrue(err.contains("API Key 无效"));
    }

    @Test
    void unknownStatusKeepsStatusCodeAndTruncatedBody() {
        String err = handler.buildHttpError(500, "internal error");

        assertTrue(err.contains("HTTP 500"));
        assertTrue(err.contains("internal error"));
    }

    @Test
    void emptyBodyIsHandled() {
        String err = handler.buildHttpError(403, null);

        assertEquals("TTS 请求失败，HTTP 403", err);
        assertFalse(err.contains("null"));
    }
}
