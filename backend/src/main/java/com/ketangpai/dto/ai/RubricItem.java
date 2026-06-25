package com.ketangpai.dto.ai;

/**
 * 评分标准维度
 */
public record RubricItem(
        String dimension,
        int weight,
        int maxScore,
        String criteria
) {}
