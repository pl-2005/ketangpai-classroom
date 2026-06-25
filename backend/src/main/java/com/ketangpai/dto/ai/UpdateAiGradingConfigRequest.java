package com.ketangpai.dto.ai;

import com.ketangpai.model.enums.GradingStyle;

/**
 * 更新 AI 批阅配置请求 DTO
 */
public record UpdateAiGradingConfigRequest(
        Boolean enabled,
        String promptTemplate,
        String rubricJson,
        GradingStyle gradingStyle
) {}
