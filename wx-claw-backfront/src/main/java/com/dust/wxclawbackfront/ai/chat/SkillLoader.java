package com.dust.wxclawbackfront.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill 加载器
 * 从 classpath:ai/skills/ 目录加载所有 SKILL.md 文件，拼接成 system prompt
 */
@Slf4j
@Component
public class SkillLoader {

    private final String skillSystemPrompt;

    public SkillLoader() {
        this.skillSystemPrompt = loadAllSkills();
    }

    /**
     * 获取拼接好的 skill system prompt
     */
    public String getSkillSystemPrompt() {
        return skillSystemPrompt;
    }

    private String loadAllSkills() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:ai/skills/**/*.md");

            if (resources.length == 0) {
                log.warn("未找到任何 skill 文件（classpath:ai/skills/**/*.md）");
                return "";
            }

            List<String> skillContents = new ArrayList<>();
            for (Resource resource : resources) {
                try {
                    String content = readResource(resource);
                    if (content != null && !content.isBlank()) {
                        skillContents.add(content.trim());
                        log.info("已加载 skill: {}", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("加载 skill 失败: {}, error={}", resource.getFilename(), e.getMessage());
                }
            }

            if (skillContents.isEmpty()) {
                return "";
            }

            // 拼接所有 skill，用分隔符分开
            StringBuilder sb = new StringBuilder();
            sb.append("# AI Skills\n\n");
            sb.append("以下是针对特定场景的专项指引（skills）。这些 skill 是对你已有能力的**补充说明**，**不影响你的其他核心能力**");
            sb.append("（如语音生成、图片理解、图片生成、工具调用等）。\n\n");
            sb.append("当用户意图匹配某个 skill 场景时，请优先参考该 skill 的指引来调用工具和组织回复。");
            sb.append("对于 skill 未覆盖的场景，请按你的正常能力处理。\n\n");
            sb.append("**重要提醒**：\n");
            sb.append("- 你**具备语音生成能力**，当用户要求发送语音/音频时，请正常生成语音回复\n");
            sb.append("- 你**具备图片理解能力**，当用户发送图片时，请正常分析并回复\n");
            sb.append("- 你**具备图片生成能力**，当用户要求生成/画图时，请正常生成图片\n");
            sb.append("- 这些能力不受以下 skills 列表的限制\n\n");
            sb.append("---\n\n");
            
            for (int i = 0; i < skillContents.size(); i++) {
                sb.append(skillContents.get(i));
                if (i < skillContents.size() - 1) {
                    sb.append("\n\n---\n\n");
                }
            }

            log.info("Skill system prompt 已构建完成，共加载 {} 个 skill，总长度 {} 字符", 
                    skillContents.size(), sb.length());
            return sb.toString();

        } catch (Exception e) {
            log.error("加载 skills 失败: {}", e.getMessage(), e);
            return "";
        }
    }

    private String readResource(Resource resource) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
