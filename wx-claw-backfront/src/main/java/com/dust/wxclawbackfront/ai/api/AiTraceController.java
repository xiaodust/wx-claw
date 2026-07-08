package com.dust.wxclawbackfront.ai.api;

import com.dust.wxclawbackfront.ai.trace.AiChatTrace;
import com.dust.wxclawbackfront.ai.trace.AiChatTraceStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/traces")
public class AiTraceController {

    private final AiChatTraceStore traceStore;

    public AiTraceController(AiChatTraceStore traceStore) {
        this.traceStore = traceStore;
    }

    @GetMapping
    public ResponseEntity<List<AiChatTrace>> list() {
        return ResponseEntity.ok(traceStore.list());
    }
}
