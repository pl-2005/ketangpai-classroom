package com.ketangpai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 — 创建专用的 ChatClient Bean 供各 AI 模块使用。
 */
@Configuration
public class AiConfig {

    /** AI 批阅专用 ChatClient */
    @Bean
    public ChatClient aiGradingChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一位严谨而公正的课程助教，负责批阅学生作业。
                        请严格按照评分标准逐项评分，输出 JSON 格式结果。
                        确保评分客观、评语有建设性、改进建议具体可操作。
                        """)
                .build();
    }
}
