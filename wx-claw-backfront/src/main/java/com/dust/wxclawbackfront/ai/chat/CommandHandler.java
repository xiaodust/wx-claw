package com.dust.wxclawbackfront.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 命令处理器
 * 处理以 # 开头的快捷命令，不经过 LLM 直接返回结果
 */
@Slf4j
@Component
public class CommandHandler {

    private static final String CURRENT_VERSION = "v1";

    /**
     * 检查是否是命令
     */
    public boolean isCommand(String text) {
        return text != null && text.startsWith("#");
    }

    /**
     * 处理命令
     * @return 命令结果，如果不是有效命令返回 null
     */
    public String handle(String commandText) {
        if (commandText == null || commandText.isBlank()) {
            return null;
        }

        String command = commandText.trim().toLowerCase();

        if ("#help".equals(command) || "#帮助".equals(command)) {
            return buildHelpMessage();
        }

        if ("#tools".equals(command) || "#工具".equals(command)) {
            return buildToolListMessage();
        }

        if ("#version".equals(command) || "#版本".equals(command)) {
            return buildVersionMessage();
        }

        // 未知命令
        return "未知命令: " + commandText + "\n\n输入 #help 查看可用命令";
    }

    /**
     * 构建帮助信息
     */
    private String buildHelpMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 可用命令 ===\n\n");
        sb.append("#help / #帮助\n");
        sb.append("  显示此帮助信息\n\n");
        sb.append("#tools / #工具\n");
        sb.append("  查看我能帮你做什么\n\n");
        sb.append("#version / #版本\n");
        sb.append("  显示当前版本信息\n\n");
        sb.append("=== 使用说明 ===\n");
        sb.append("直接输入 #命令 即可执行，无需等待 AI 回复");
        return sb.toString();
    }

    /**
     * 构建功能列表信息
     */
    private String buildToolListMessage() {
        return """
                === 我能做什么 ===

                我是你的 AI 智能助手，可以帮你完成以下任务：

                🕐 时间查询
                   告诉我现在几点、今天日期、星期几等

                🌤 天气查询
                   查询任意城市的实时天气和未来预报

                🔍 网络搜索
                   帮你搜索最新资讯、新闻、百科知识等

                ⏰ 提醒设置
                   设置一次性或周期性提醒（每天/每周/每月）

                📊 对话总结
                   生成日报、周报、月报，总结我们的对话内容

                🧠 记忆功能
                   记住你的偏好和习惯，提供更个性化的服务

                📧 邮件发送
                   帮你发送邮件通知

                🖼 图片生成
                   根据描述生成图片

                🎙 语音处理
                   生成语音回复

                ---
                直接用自然语言告诉我你的需求，我会自动选择合适的功能来帮助你！""";
    }

    /**
     * 构建版本信息
     */
    private String buildVersionMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 版本信息 ===\n\n");
        sb.append("当前版本: ").append(CURRENT_VERSION).append("\n");
        sb.append("项目名称: WX-Claw AI 助手\n");
        sb.append("功能特性:\n");
        sb.append("  - 智能对话\n");
        sb.append("  - 工具调用（天气、搜索、提醒等）\n");
        sb.append("  - 对话总结（日报/周报/月报）\n");
        sb.append("  - 用户记忆\n");
        sb.append("  - 图片/语音处理\n");
        return sb.toString();
    }
}
