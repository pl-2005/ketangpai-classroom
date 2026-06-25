package com.ketangpai.dto.ai;

import java.util.List;

/**
 * Rubric 校验结果
 */
public record RubricValidationResult(
        boolean valid,
        List<String> errors,
        int totalWeight,
        int totalMaxScore
) {}
