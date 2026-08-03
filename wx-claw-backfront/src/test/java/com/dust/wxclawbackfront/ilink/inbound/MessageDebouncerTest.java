package com.dust.wxclawbackfront.ilink.inbound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDebouncerTest {

    private final MessageDebouncer debouncer = new MessageDebouncer();

    @Test
    void sameTenantSameUserSameTextWithinWindowIsSkipped() {
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "帮我推荐岗位"));
        assertFalse(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "帮我推荐岗位"));
    }

    @Test
    void sameUserInDifferentTenantsIsNotSkipped() {
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "你好"));
        // 不同租户下 openId 可能相同，不应共享防抖窗口
        assertTrue(debouncer.shouldProcess("tenant-b", "bot-2", "user-1", "你好"));
    }

    @Test
    void differentBotsSameTenantIsNotSkipped() {
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "你好"));
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-2", "user-1", "你好"));
    }

    @Test
    void differentTextIsNotSkipped() {
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "你好"));
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "天气怎么样"));
    }

    @Test
    void blankTextAlwaysPasses() {
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", null));
        assertTrue(debouncer.shouldProcess("tenant-a", "bot-1", "user-1", "   "));
    }
}
