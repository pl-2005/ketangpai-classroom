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

    /** RAG 答疑专用 ChatClient — 基于课程知识库回答学生问题 */
    @Bean
    public ChatClient aiChatChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是一位专业的课程AI助教，负责回答学生关于课程内容的问题。

                        回答规则：
                        1. 请严格根据提供的「课程资料」片段回答问题，不要使用课程资料之外的知识
                        2. 如果课程资料中有明确相关信息，请引用具体来源，使用 [来源: 资料名称] 的格式标注
                        3. 如果课程资料中没有相关信息，请诚实告知学生「课程资料中暂未找到相关信息」
                        4. 回答要清晰、有条理、有针对性，尽量引用原文片段
                        5. 对于需要总结的问题，请归纳多个资料片段，形成完整解答
                        """)
                .build();
    }
}
