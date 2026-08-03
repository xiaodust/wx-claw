package com.dust.wxclawbackfront.bot.agent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词加载器。
 *
 * <p>从 classpath:ai/prompts/ 目录按名称加载 Markdown 提示词模板，支持两种占位符：
 * <ul>
 *   <li>{{变量名}}：普通变量替换；</li>
 *   <li>{{#段名}}...{{/段名}}：条件段，按开关保留或整段移除。</li>
 * </ul>
 *
 * <p>每次调用都会重新读取文件，便于直接热改提示词；文件缺失、变量缺失或条件段未闭合
 * 都会立即抛出异常（快速失败），避免带着残缺提示词继续运行。
 */
@Slf4j
@Component
public class PromptLoader {

    private static final Pattern SECTION_PATTERN = Pattern.compile("\\{\\{#(\\w+)\\}\\}([\\s\\S]*?)\\{\\{/\\1\\}\\}");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern SECTION_MARKER_PATTERN = Pattern.compile("\\{\\{[/#]\\w+\\}\\}");
    private static final Pattern NEWLINE_RUN_PATTERN = Pattern.compile("\\n{3,}");

    private static final String PROMPT_ROOT = "classpath:ai/prompts/**/*.md";

    private final boolean careerEnabled;

    public PromptLoader(@Value("${wxclaw.career.enabled:false}") boolean careerEnabled) {
        this.careerEnabled = careerEnabled;
    }

    /**
     * 渲染指定提示词。
     *
     * @param name      提示词文件名（不含 .md 后缀），如 "agent-planner"
     * @param variables 变量替换表，键为占位符名
     * @param sections  条件段开关；career_enabled 由配置决定，无需传入
     * @return 渲染后的提示词文本
     */
    public String render(String name, Map<String, String> variables, Map<String, Boolean> sections) {
        String template = readPrompt(name);
        Map<String, Boolean> effectiveSections = new HashMap<>(sections == null ? Map.of() : sections);
        effectiveSections.putIfAbsent("career_enabled", careerEnabled);

        // 1. 处理条件段：保留或移除段内内容
        Matcher sectionMatcher = SECTION_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (sectionMatcher.find()) {
            String sectionName = sectionMatcher.group(1);
            Boolean enabled = effectiveSections.get(sectionName);
            if (enabled == null) {
                throw new IllegalArgumentException("提示词 " + name + " 存在未定义的条件段: " + sectionName);
            }
            sectionMatcher.appendReplacement(sb, Matcher.quoteReplacement(enabled ? sectionMatcher.group(2) : ""));
        }
        sectionMatcher.appendTail(sb);
        String rendered = sb.toString();

        // 条件段必须全部闭合
        Matcher leftover = SECTION_MARKER_PATTERN.matcher(rendered);
        if (leftover.find()) {
            throw new IllegalArgumentException("提示词 " + name + " 存在未匹配的条件段标记: " + leftover.group());
        }

        // 2. 变量替换
        Matcher varMatcher = VARIABLE_PATTERN.matcher(rendered);
        sb = new StringBuffer();
        while (varMatcher.find()) {
            String varName = varMatcher.group(1);
            if (variables == null || !variables.containsKey(varName)) {
                throw new IllegalArgumentException("提示词 " + name + " 缺少变量: " + varName);
            }
            varMatcher.appendReplacement(sb, Matcher.quoteReplacement(variables.get(varName)));
        }
        varMatcher.appendTail(sb);
        rendered = sb.toString();

        if (rendered.contains("{{")) {
            throw new IllegalArgumentException("提示词 " + name + " 存在未解析的占位符");
        }

        // 3. 条件段移除后收敛多余空行，保持模板干净
        return NEWLINE_RUN_PATTERN.matcher(rendered).replaceAll("\n\n");
    }

    private String readPrompt(String name) {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PROMPT_ROOT);
            for (Resource resource : resources) {
                if ((name + ".md").equals(resource.getFilename())) {
                    return readResource(resource);
                }
            }
            throw new IllegalStateException("提示词文件不存在: ai/prompts/" + name + ".md");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("加载提示词失败: ai/prompts/" + name + ".md, error=" + e.getMessage(), e);
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
