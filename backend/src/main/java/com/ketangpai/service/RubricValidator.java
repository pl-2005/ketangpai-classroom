package com.ketangpai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.dto.ai.RubricItem;
import com.ketangpai.dto.ai.RubricValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 评分标准（Rubric）校验器：
 * - JSON 可解析
 * - 至少一个维度
 * - 维度名/标准非空
 * - 权重为正整数且总和 = 100
 * - 各维度满分为正整数
 * - Prompt 模板含必需占位符
 */
@Component
public class RubricValidator {

    private static final Set<String> REQUIRED_PLACEHOLDERS = Set.of(
            "{rubric}", "{submission}", "{maxScore}"
    );

    private final ObjectMapper objectMapper;

    public RubricValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 rubricJson 和 promptTemplate。
     *
     * @param rubricJson    评分标准 JSON 字符串
     * @param promptTemplate Prompt 模板（可为 null）
     * @param maxScore      作业满分，用于校验各维度满分之和
     */
    public RubricValidationResult validate(String rubricJson, String promptTemplate, int maxScore) {
        List<String> errors = new ArrayList<>();
        int totalWeight = 0;
        int totalMaxScore = 0;

        // 1. 解析 rubricJson
        List<RubricItem> items;
        try {
            items = objectMapper.readValue(rubricJson, new TypeReference<List<RubricItem>>() {});
        } catch (Exception e) {
            errors.add("评分标准 JSON 格式无效：" + e.getMessage());
            return new RubricValidationResult(false, errors, 0, 0);
        }

        if (items == null || items.isEmpty()) {
            errors.add("评分标准至少需要一个维度");
            return new RubricValidationResult(false, errors, 0, 0);
        }

        // 2. 逐项校验
        for (int i = 0; i < items.size(); i++) {
            RubricItem item = items.get(i);
            String prefix = "维度[" + (i + 1) + "]：";

            if (item.dimension() == null || item.dimension().isBlank()) {
                errors.add(prefix + "名称不能为空");
            }
            if (item.criteria() == null || item.criteria().isBlank()) {
                errors.add(prefix + "评分标准不能为空");
            }
            if (item.weight() <= 0) {
                errors.add(prefix + "权重必须为正整数，当前值：" + item.weight());
            }
            if (item.maxScore() <= 0) {
                errors.add(prefix + "满分必须为正整数，当前值：" + item.maxScore());
            }

            totalWeight += item.weight();
            totalMaxScore += item.maxScore();
        }

        // 3. 权重校验
        if (totalWeight != 100) {
            errors.add("所有维度权重之和必须为 100，当前总和：" + totalWeight);
        }

        // 4. 满分校验：各维度满分之和不应超过作业满分
        if (totalMaxScore > maxScore) {
            errors.add(String.format("各维度满分之和(%d)超过作业满分(%d)", totalMaxScore, maxScore));
        }

        // 5. Prompt 模板占位符校验
        if (promptTemplate != null && !promptTemplate.isBlank()) {
            for (String placeholder : REQUIRED_PLACEHOLDERS) {
                if (!promptTemplate.contains(placeholder)) {
                    errors.add("Prompt 模板缺少必需占位符：" + placeholder);
                }
            }
        }

        return new RubricValidationResult(errors.isEmpty(), errors, totalWeight, totalMaxScore);
    }
}
