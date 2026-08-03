package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.WxClawBackfrontApplication;
import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线提示词评测（不进默认 CI）。
 *
 * <p>用真实规划模型跑 {@code prompt-eval/planner-golden-cases.json} 中的黄金用例，
 * 校验规划模型能否给出与预期一致的工具序列。运行方式：</p>
 * <pre>
 *   mvn test -Dgroups=prompt-eval -DexcludedGroups=
 * </pre>
 *
 * <p>默认 surefire 通过 excludedGroups 排除本用例；普通 {@code mvn test} 不会执行。</p>
 */
@Tag("prompt-eval")
@SpringBootTest(classes = WxClawBackfrontApplication.class)
@ActiveProfiles("test")
@Testcontainers
class PromptGoldenEvalRunner {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39");

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private PlainTextLlmService plainTextLlmService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void runGoldenCasesWithRealPlanningModel() throws Exception {
        JsonNode root = objectMapper.readTree(
                new ClassPathResource("prompt-eval/planner-golden-cases.json").getInputStream());
        List<String> failures = new ArrayList<>();
        int total = 0;
        for (JsonNode node : root) {
            total++;
            String name = node.get("name").asText();
            String message = node.get("message").asText();
            List<String> expected = new ArrayList<>();
            node.get("expectedTools").forEach(t -> expected.add(t.asText()));

            String prompt = orchestrator.buildPlanningPrompt(message,
                    AgentContext.builder().userText(message).build());
            String response = plainTextLlmService.chat(prompt, "PLAN");
            List<String> actual = parseTools(response);

            if (!expected.equals(actual)) {
                failures.add(name + " | expected=" + expected + " actual=" + actual);
            }
        }

        System.out.println("[prompt-eval] 通过 " + (total - failures.size()) + "/" + total + " 个黄金用例");
        failures.forEach(f -> System.out.println("[prompt-eval] 失败: " + f));

        assertThat(failures).as("提示词规划质量未达基线").isEmpty();
    }

    private List<String> parseTools(String response) throws Exception {
        List<String> tools = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return tools;
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return tools;
        }
        JsonNode node = objectMapper.readTree(response.substring(start, end + 1));
        JsonNode steps = node.get("steps");
        if (steps != null && steps.isArray()) {
            steps.forEach(step -> tools.add(step.get("tool").asText()));
        }
        return tools;
    }
}
