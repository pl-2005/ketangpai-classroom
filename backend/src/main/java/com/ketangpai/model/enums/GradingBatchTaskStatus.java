package com.ketangpai.model.enums;

/**
 * AI 批阅批量任务状态
 */
public enum GradingBatchTaskStatus {
    /** 待执行 */
    PENDING,
    /** 执行中 */
    IN_PROGRESS,
    /** 全部完成 */
    COMPLETED,
    /** 部分失败（部分提交批阅成功） */
    PARTIALLY_FAILED,
    /** 全部失败 */
    FAILED
}
