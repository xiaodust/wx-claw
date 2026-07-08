package com.dust.wxclawbackfront.ai.trace;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class AiChatTraceStore {

    private final ConcurrentLinkedDeque<AiChatTrace> deque = new ConcurrentLinkedDeque<>();
    private final int maxSize = 200;

    public void add(AiChatTrace trace) {
        if (trace == null) {
            return;
        }
        deque.addLast(trace);
        while (deque.size() > maxSize) {
            deque.pollFirst();
        }
    }

    public List<AiChatTrace> list() {
        if (deque.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiChatTrace> list = new ArrayList<>(deque);
        Collections.reverse(list);
        return list;
    }
}
