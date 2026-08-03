package com.dust.wxclawbackfront.bot.agent.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    @Test
    void rendersAgentPlannerWithVariables() {
        PromptLoader loader = new PromptLoader(true);
        String prompt = loader.render("agent-planner", Map.of(
                "user_message", "给我讲个故事",
                "history", "用户: 你好\n",
                "user_profiles", "姓名: 张三\n"), Map.of("history", true, "user_profiles", true));

        assertThat(prompt).contains("## 用户消息\n\n给我讲个故事");
        assertThat(prompt).contains("## 历史对话（仅用于解析当前追问）");
        assertThat(prompt).contains("用户: 你好");
        assertThat(prompt).contains("## 用户信息");
        assertThat(prompt).contains("姓名: 张三");
        assertThat(prompt).contains("career_job_search");
        assertThat(prompt).doesNotContain("{{");
        assertThat(prompt).doesNotContain("\n\n\n");
    }

    @Test
    void omitsEmptySectionsAndCareerWhenDisabled() {
        PromptLoader loader = new PromptLoader(false);
        String prompt = loader.render("agent-planner", Map.of(
                "user_message", "hi", "history", "", "user_profiles", ""),
                Map.of("history", false, "user_profiles", false));

        assertThat(prompt).doesNotContain("## 历史对话");
        assertThat(prompt).doesNotContain("## 用户信息");
        assertThat(prompt).doesNotContain("career_job_search");
        assertThat(prompt).doesNotContain("career_resume_score");
        assertThat(prompt).contains("knowledge_file_retrieve");
        assertThat(prompt).doesNotContain("\n\n\n");
    }

    @Test
    void throwsWhenVariableMissingInEnabledSection() {
        PromptLoader loader = new PromptLoader(true);
        assertThatThrownBy(() -> loader.render("agent-planner", Map.of("user_message", "hi", "user_profiles", ""),
                Map.of("history", true, "user_profiles", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少变量");
    }

    @Test
    void throwsWhenPromptFileMissing() {
        PromptLoader loader = new PromptLoader(true);
        assertThatThrownBy(() -> loader.render("no-such-prompt", Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("提示词文件不存在");
    }
}
