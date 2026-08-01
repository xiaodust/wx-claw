package com.dust.wxclawbackfront.bot.agent.career.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerQueryNormalizerTest {
    @Test
    void parsesStructuredQueryFromAiResponse() {
        PlainTextLlmService llm = mock(PlainTextLlmService.class);
        when(llm.chat(anyString(), anyString())).thenReturn(
                "{\"locations\":[\"杭州\"],\"include_keywords\":[\"Java\",\"腾讯\"],"
                        + "\"exclude_keywords\":[],\"employment_types\":[\"INTERNSHIP\"]}");
        CareerQueryNormalizer normalizer = new CareerQueryNormalizer(llm, new ObjectMapper());

        CareerQueryNormalizer.NormalizedQuery result = normalizer.normalize("推荐杭州腾讯 Java 实习岗位",
                new CareerQueryNormalizer.NormalizedQuery(List.of(), List.of(), List.of(), List.of(), null));

        assertThat(result.locations()).containsExactly("杭州");
        assertThat(result.includeKeywords()).containsExactly("Java", "腾讯");
        assertThat(result.employmentTypes()).containsExactly("INTERNSHIP");
    }
}
