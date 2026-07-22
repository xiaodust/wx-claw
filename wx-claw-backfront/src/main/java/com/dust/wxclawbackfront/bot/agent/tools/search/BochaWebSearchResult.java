package com.dust.wxclawbackfront.bot.agent.tools.search;

import lombok.Getter;

import java.util.List;

@Getter
public final class BochaWebSearchResult {

    private final String requestJson;
    private final String responseJson;
    private final String query;
    private final String freshness;
    private final Integer count;
    private final String errorMsg;
    private final List<Item> items;

    public BochaWebSearchResult(String requestJson,
                                String responseJson,
                                String query,
                                String freshness,
                                Integer count,
                                String errorMsg,
                                List<Item> items) {
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.query = query;
        this.freshness = freshness;
        this.count = count;
        this.errorMsg = errorMsg;
        this.items = items;
    }

    @Getter
    public static final class Item {
        private final String name;
        private final String url;
        private final String snippet;
        private final String summary;
        private final String siteName;
        private final String datePublished;

        public Item(String name, String url, String snippet, String summary, String siteName, String datePublished) {
            this.name = name;
            this.url = url;
            this.snippet = snippet;
            this.summary = summary;
            this.siteName = siteName;
            this.datePublished = datePublished;
        }
    }
}
