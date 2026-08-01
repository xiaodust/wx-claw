package com.dust.wxclawbackfront.ilink.inbound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ILinkMessageDispatcherIntentTest {
    @Test
    void buildsImmediateMediaExecutionPrompt() {
        String prompt = ILinkMessageDispatcher.buildPendingMediaTaskPrompt(
                "图片", "画面中有一张数据表", "提取表格内容");

        assertTrue(prompt.contains("第一条处理指令"));
        assertTrue(prompt.contains("请立即"));
        assertTrue(prompt.contains("不要再次询问"));
        assertTrue(prompt.contains("画面中有一张数据表"));
        assertTrue(prompt.contains("提取表格内容"));
    }
}
