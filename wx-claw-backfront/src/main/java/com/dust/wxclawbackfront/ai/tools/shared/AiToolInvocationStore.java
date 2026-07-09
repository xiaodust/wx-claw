package com.dust.wxclawbackfront.ai.tools.shared;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiToolInvocationStore {

    private final ThreadLocal<List<Invocation>> holder = ThreadLocal.withInitial(ArrayList::new);

    public void reset() {
        holder.get().clear();
    }

    public void add(String toolName, String toolRequest, String toolResponse) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        holder.get().add(new Invocation(toolName, toolRequest, toolResponse));
    }

    public List<Invocation> drain() {
        List<Invocation> list = new ArrayList<>(holder.get());
        holder.get().clear();
        return list;
    }

    public record Invocation(String toolName, String toolRequest, String toolResponse) {
    }
}

