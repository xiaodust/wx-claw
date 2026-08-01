package com.dust.wxclawbackfront.bot.agent.career.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CareerSearchContextStore {
    private final ConcurrentMap<String, SearchState> states = new ConcurrentHashMap<>();

    public SearchState get(String userId) {
        return userId == null ? null : states.get(userId);
    }

    public void put(String userId, CareerQueryNormalizer.NormalizedQuery query, int page) {
        if (userId != null && !userId.isBlank() && query != null) {
            states.put(userId, new SearchState(query, page));
        }
    }

    public record SearchState(CareerQueryNormalizer.NormalizedQuery query, int page) {
    }
}
