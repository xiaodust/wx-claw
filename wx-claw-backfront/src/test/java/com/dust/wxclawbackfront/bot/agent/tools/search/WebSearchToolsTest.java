package com.dust.wxclawbackfront.bot.agent.tools.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSearchToolsTest {

    @Test
    void delegatesSearchToHandler() {
        BochaWebSearchHandler handler = mock(BochaWebSearchHandler.class);
        WebSearchTools tools = new WebSearchTools(handler);
        BochaWebSearchResult handlerResult = new BochaWebSearchResult(
                "request", "response", "杭州天气", "oneDay", 3, null, List.of());
        when(handler.search("杭州天气", "oneDay", 3)).thenReturn(handlerResult);

        WebSearchTools.WebSearchToolResult result = tools.search("杭州天气", "oneDay", 3);

        assertThat(result.items()).isEmpty();
        assertThat(result.errorMsg()).isNull();
        verify(handler).search("杭州天气", "oneDay", 3);
    }
}
