package com.dust.wxclawbackfront.bot.agent.orchestrator;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.model.AgentContext;
import com.dust.wxclawbackfront.bot.agent.model.AgentResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskPlan;
import com.dust.wxclawbackfront.bot.agent.model.TaskResult;
import com.dust.wxclawbackfront.bot.agent.model.TaskStep;
import com.dust.wxclawbackfront.bot.agent.orchestrator.executor.TaskExecutor;
import com.dust.wxclawbackfront.bot.agent.prompt.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 编排黄金用例：规划模型返回既定 JSON 计划时，编排器必须按计划调度对应工具；
 * 规划输出非法时，必须安全兜底为 chat。全部使用真实 PlanValidator 校验。
 */
class AgentOrchestratorPlanDispatchTest {

    private record Case(String name, String message, String planJson, List<String> expectedTools) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("纯闲聊走chat", "你好呀",
                        "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"闲聊\"}]}",
                        List.of("chat")),
                new Case("天气查询走chat", "杭州今天天气怎么样",
                        "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"天气\"}]}",
                        List.of("chat")),
                new Case("纯语音问候走voice", "给我发个早上问候语音",
                        "{\"steps\":[{\"step\":1,\"tool\":\"voice_synthesize\",\"params\":{},\"description\":\"语音\"}]}",
                        List.of("voice_synthesize")),
                new Case("创作后朗读拆两步", "讲个故事然后读给我听",
                        "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{\"input\":\"讲个故事\"},\"description\":\"故事\"},{\"step\":2,\"tool\":\"voice_synthesize\",\"params\":{\"text\":\"{step_1_result}\"},\"depends_on\":1,\"description\":\"朗读\"}]}",
                        List.of("chat", "voice_synthesize")),
                new Case("生成图片走image", "画一张小猫图片",
                        "{\"steps\":[{\"step\":1,\"tool\":\"image_generate\",\"params\":{\"prompt\":\"小猫\"},\"description\":\"画图\"}]}",
                        List.of("image_generate")),
                new Case("生成视频走video", "帮我做个生日视频",
                        "{\"steps\":[{\"step\":1,\"tool\":\"video_generate\",\"params\":{},\"description\":\"视频\"}]}",
                        List.of("video_generate")),
                new Case("简历评分走score", "给我的简历打个分",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_resume_score\",\"params\":{},\"description\":\"评分\"}]}",
                        List.of("career_resume_score")),
                new Case("简历分析走analyze", "帮我分析一下我的简历",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_resume_analyze\",\"params\":{},\"description\":\"分析\"}]}",
                        List.of("career_resume_analyze")),
                new Case("简历取回走retrieve", "把我的简历发给我",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_resume_retrieve\",\"params\":{},\"description\":\"取回\"}]}",
                        List.of("career_resume_retrieve")),
                new Case("简历清除走clear", "忘记我的简历吧",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_resume_clear\",\"params\":{},\"description\":\"清除\"}]}",
                        List.of("career_resume_clear")),
                new Case("根据简历推荐岗位", "根据我的简历推荐杭州后端实习岗位",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_job_recommendation\",\"params\":{\"input\":\"根据我的简历推荐杭州后端实习岗位\",\"locations\":[\"杭州\"],\"include_keywords\":[\"后端\"],\"employment_types\":[\"INTERNSHIP\"]},\"description\":\"推荐\"}]}",
                        List.of("career_job_recommendation")),
                new Case("普通岗位搜索走search", "推荐一些杭州后端实习岗位",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_job_search\",\"params\":{\"input\":\"推荐一些杭州后端实习岗位\",\"locations\":[\"杭州\"],\"include_keywords\":[\"后端\"],\"employment_types\":[\"INTERNSHIP\"]},\"description\":\"搜索\"}]}",
                        List.of("career_job_search")),
                new Case("复合请求拆四步", "给我讲个故事然后画个小猫图片再给我语音发一条早上问候消息 最后再帮我推荐一些杭州后端实习岗位",
                        "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{\"input\":\"给我讲个故事\"},\"description\":\"故事\"},{\"step\":2,\"tool\":\"image_generate\",\"params\":{\"prompt\":\"一只小猫\",\"input\":\"画个小猫图片\"},\"description\":\"画图\"},{\"step\":3,\"tool\":\"voice_synthesize\",\"params\":{\"text\":\"{step_1_result}\",\"input\":\"语音发一条早上问候消息\"},\"depends_on\":1,\"description\":\"语音\"},{\"step\":4,\"tool\":\"career_job_search\",\"params\":{\"input\":\"推荐一些杭州后端实习岗位\",\"locations\":[\"杭州\"],\"include_keywords\":[\"后端\"],\"employment_types\":[\"INTERNSHIP\"]},\"description\":\"搜索\"}]}",
                        List.of("chat", "image_generate", "voice_synthesize", "career_job_search")),
                new Case("岗位追问扩大范围", "扩大到全国",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_job_search\",\"params\":{\"input\":\"扩大到全国\",\"locations\":[\"全国\"]},\"description\":\"搜索\"}]}",
                        List.of("career_job_search")),
                new Case("岗位追问只要社招", "只要社招",
                        "{\"steps\":[{\"step\":1,\"tool\":\"career_job_search\",\"params\":{\"input\":\"只要社招\",\"employment_types\":[\"SOCIAL\"]},\"description\":\"搜索\"}]}",
                        List.of("career_job_search")),
                new Case("知识库取回文件", "把我的知识库原始文件发给我",
                        "{\"steps\":[{\"step\":1,\"tool\":\"knowledge_file_retrieve\",\"params\":{},\"description\":\"取回\"}]}",
                        List.of("knowledge_file_retrieve")),
                new Case("图片后语音依赖前步结果", "生成图片后朗读描述",
                        "{\"steps\":[{\"step\":1,\"tool\":\"image_generate\",\"params\":{\"prompt\":\"小猫\"},\"description\":\"画图\"},{\"step\":2,\"tool\":\"voice_synthesize\",\"params\":{\"text\":\"{step_1_result}\"},\"depends_on\":1,\"description\":\"朗读\"}]}",
                        List.of("image_generate", "voice_synthesize")),
                new Case("规划非JSON兜底chat", "给我发个早上问候语音", "这不是JSON",
                        List.of("chat")),
                new Case("规划缺少steps兜底chat", "给我讲个故事", "{}",
                        List.of("chat")),
                new Case("重复chat步骤校验失败兜底chat", "帮我做点事",
                        "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"a\"},{\"step\":2,\"tool\":\"chat\",\"params\":{},\"description\":\"b\"}]}",
                        List.of("chat")),
                new Case("未知工具校验失败兜底chat", "随便处理一下",
                        "{\"steps\":[{\"step\":1,\"tool\":\"unknown_tool\",\"params\":{},\"description\":\"x\"}]}",
                        List.of("chat")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void dispatchesPerPlannerPlan(Case c) {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), new PlanValidator(new ObjectMapper()),
                new PromptLoader(true));
        AgentContext context = AgentContext.builder().userText(c.message()).build();
        when(planningModel.chat(any(), eq("PLAN"))).thenReturn(c.planJson());

        List<String> executedTools = new ArrayList<>();
        when(taskExecutor.execute(any(), same(context))).thenAnswer(invocation -> {
            TaskPlan plan = invocation.getArgument(0);
            executedTools.addAll(plan.getSteps().stream().map(TaskStep::getToolName).toList());
            return IntStream.range(0, plan.getStepCount()).mapToObj(i -> TaskResult.success("ok", 1)).toList();
        });
        when(taskExecutor.mergeResults(any())).thenAnswer(invocation -> {
            List<TaskResult> results = invocation.getArgument(0);
            return TaskResult.success("merged", results.stream().mapToLong(TaskResult::getExecutionTimeMs).sum());
        });

        AgentResult result = orchestrator.orchestrate(c.message(), context);

        assertThat(executedTools).as(c.name()).containsExactlyElementsOf(c.expectedTools());
        assertThat(result.isSuccess()).as(c.name()).isTrue();
    }

    @org.junit.jupiter.api.Test
    void planningPromptContainsInputConventionAndCareerTools() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), new PlanValidator(new ObjectMapper()),
                new PromptLoader(true));
        AtomicReference<String> promptRef = new AtomicReference<>();
        when(planningModel.chat(any(), eq("PLAN"))).thenAnswer(invocation -> {
            promptRef.set(invocation.getArgument(0));
            return "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"x\"}]}";
        });
        when(taskExecutor.execute(any(), any())).thenReturn(List.of(TaskResult.success("ok", 1)));

        orchestrator.orchestrate("给我讲个故事", AgentContext.builder().userText("给我讲个故事").build());

        String prompt = promptRef.get();
        assertThat(prompt).contains("params.input");
        assertThat(prompt).contains("7.1 【分句】");
        assertThat(prompt).contains("career_job_search");
        assertThat(prompt).contains("career_resume_score");
    }

    @org.junit.jupiter.api.Test
    void careerDisabledPromptOmitsCareerTools() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), new PlanValidator(new ObjectMapper()),
                new PromptLoader(false));
        AtomicReference<String> promptRef = new AtomicReference<>();
        when(planningModel.chat(any(), eq("PLAN"))).thenAnswer(invocation -> {
            promptRef.set(invocation.getArgument(0));
            return "{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{},\"description\":\"x\"}]}";
        });
        when(taskExecutor.execute(any(), any())).thenReturn(List.of(TaskResult.success("ok", 1)));

        orchestrator.orchestrate("给我讲个故事", AgentContext.builder().userText("给我讲个故事").build());

        String prompt = promptRef.get();
        assertThat(prompt).doesNotContain("career_job_search");
        assertThat(prompt).doesNotContain("career_resume_score");
        assertThat(prompt).contains("knowledge_file_retrieve");
    }

    @org.junit.jupiter.api.Test
    void planWithInputClauseKeepsStepParams() {
        PlainTextLlmService planningModel = mock(PlainTextLlmService.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                planningModel, taskExecutor, new ObjectMapper(), new PlanValidator(new ObjectMapper()),
                new PromptLoader(true));
        when(planningModel.chat(any(), eq("PLAN")))
                .thenReturn("{\"steps\":[{\"step\":1,\"tool\":\"chat\",\"params\":{\"input\":\"给我讲个故事\"},\"description\":\"x\"}]}");
        AtomicReference<TaskPlan> captured = new AtomicReference<>();
        when(taskExecutor.execute(any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return List.of(TaskResult.success("ok", 1));
        });

        orchestrator.orchestrate("给我讲个故事然后画个小猫图片", AgentContext.builder().userText("给我讲个故事然后画个小猫图片").build());

        assertThat(captured.get().getSteps().get(0).getParams().get("input")).isEqualTo("给我讲个故事");
    }
}
