package com.ketangpai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI 批阅结构化输出 — Spring AI BeanOutputConverter 使用此 record 生成 JSON Schema。
 */
public record AiGradingResponse(
        @JsonProperty(required = true) int score,
        @JsonProperty(required = true) String comment,
        @JsonProperty(required = true) String suggestions,
        @JsonProperty(required = true) List<DimensionScore> dimensions
) {
    public record DimensionScore(
            @JsonProperty(required = true) String dimension,
            @JsonProperty(required = true) int score,
            @JsonProperty(required = true) int maxScore,
            @JsonProperty(required = true) String comment
    ) {}
}
