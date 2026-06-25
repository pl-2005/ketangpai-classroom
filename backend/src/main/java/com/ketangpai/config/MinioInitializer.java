package com.ketangpai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MinIO 初始化 — 确保 bucket 存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioInitializer {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        try {
            String bucket = minioConfig.getBucket();
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket 已创建: {}", bucket);
            } else {
                log.info("MinIO bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败", e);
        }
    }
}
