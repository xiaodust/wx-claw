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
            sb.append("你拥有以下专项技能（skills），当用户意图匹配时，请按照对应 skill 的指引来调用工具和组织回复：\n\n");
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
