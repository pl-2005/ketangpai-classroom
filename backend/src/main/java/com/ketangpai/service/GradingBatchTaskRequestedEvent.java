package com.ketangpai.service;

import java.util.List;

public record GradingBatchTaskRequestedEvent(Long taskId, List<Long> submissionIds) {
}
