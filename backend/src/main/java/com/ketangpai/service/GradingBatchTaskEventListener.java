package com.ketangpai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GradingBatchTaskEventListener {

    private final AiGradingService aiGradingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(GradingBatchTaskRequestedEvent event) {
        aiGradingService.processBatch(event.taskId(), event.submissionIds());
    }
}
