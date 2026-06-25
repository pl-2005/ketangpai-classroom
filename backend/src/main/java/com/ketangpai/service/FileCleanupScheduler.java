package com.ketangpai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文件清理定时任务。
 * <p>
 * 每小时执行一次，清理超过 24 小时未被关联的孤儿文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileService fileService;

    /** 每小时的第 7 分钟执行（避免整点高峰期） */
    @Scheduled(cron = "0 7 * * * *")
    public void cleanupOrphanFiles() {
        log.info("开始定时清理孤儿文件...");
        try {
            fileService.cleanupTempFiles();
        } catch (Exception e) {
            log.error("孤儿文件清理任务执行失败", e);
        }
    }
}
