package com.dust.wxclawbackfront.ai.tools.memory;

import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户记忆工具
 * 让 AI 能够读写用户画像和学习规则
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryTools {

    private final UserMemoryService memoryService;
    private final AiToolInvocationStore invocationStore;

    @Tool(name = "update_user_profile", description = "记录或更新用户的个人信息（如城市、职业、偏好、习惯、作息等）。当用户在对话中自然透露个人信息时调用。category可选: basic_info(基本信息)/preference(偏好)/habit(习惯)/decision(决策)")
    public UserProfileResult updateProfile(
            @ToolParam(description = "分类: basic_info / preference / habit / decision") String category,
            @ToolParam(description = "信息键名，如 city / job / reply_style / sleep_time") String key,
            @ToolParam(description = "信息值") String value) {
        invocationStore.add("update_user_profile", "category=" + category + ", key=" + key + ", value=" + value, null);

        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new UserProfileResult(false, "无法获取用户ID");
        }

        try {
            memoryService.saveProfile(userId, category, key, value, "user_told");
            return new UserProfileResult(true, "已记录: " + key + " = " + value);
        } catch (Exception e) {
            log.error("保存用户画像失败", e);
            return new UserProfileResult(false, "记录失败: " + e.getMessage());
        }
    }

    @Tool(name = "add_user_learning", description = "记录用户的学习指令。当用户说\"以后xxx的时候要xxx\"、\"下次总结时xxx\"、\"记住xxx\"等指示性语言时调用。trigger为触发场景(如summary/daily_report/reply/general)，instruction为用户的具体要求")
    public UserLearningResult addLearning(
            @ToolParam(description = "触发场景，如 summary / daily_report / reply / general") String trigger,
            @ToolParam(description = "用户的具体要求或指令") String instruction) {
        invocationStore.add("add_user_learning", "trigger=" + trigger + ", instruction=" + instruction, null);

        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new UserLearningResult(false, "无法获取用户ID");
        }

        try {
            memoryService.saveLearning(userId, trigger, instruction);
            return new UserLearningResult(true, "已记住: 当" + trigger + "时，" + instruction);
        } catch (Exception e) {
            log.error("保存学习规则失败", e);
            return new UserLearningResult(false, "记录失败: " + e.getMessage());
        }
    }

    @Tool(name = "list_user_memory", description = "查看当前用户的记忆数据，包括用户画像和学习规则。用于调试或用户查询自己的记忆")
    public UserMemoryView listMemory() {
        invocationStore.add("list_user_memory", null, null);

        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            return new UserMemoryView(List.of(), List.of());
        }

        List<com.dust.wxclawbackfront.ai.dao.entity.UserProfile> profiles = memoryService.getProfiles(userId);
        List<com.dust.wxclawbackfront.ai.dao.entity.UserLearning> learnings = memoryService.getActiveLearnings(userId);

        List<ProfileEntry> profileEntries = profiles.stream()
                .map(p -> new ProfileEntry(p.getCategory(), p.getKeyName(), p.getKeyValue()))
                .toList();

        List<LearningEntry> learningEntries = learnings.stream()
                .map(l -> new LearningEntry(l.getId(), l.getTrigger(), l.getInstruction()))
                .toList();

        return new UserMemoryView(profileEntries, learningEntries);
    }

    public record UserProfileResult(boolean success, String message) {}
    public record UserLearningResult(boolean success, String message) {}
    public record ProfileEntry(String category, String key, String value) {}
    public record LearningEntry(Long id, String trigger, String instruction) {}
    public record UserMemoryView(List<ProfileEntry> profiles, List<LearningEntry> learnings) {}
}
