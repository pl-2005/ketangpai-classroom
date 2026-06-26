package com.ketangpai.service;

import com.ketangpai.config.MinioConfig;
import com.ketangpai.exception.BusinessException;
import com.ketangpai.model.entity.TempFile;
import com.ketangpai.repository.TempFileRepository;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传管理服务
 * <p>
 * 采用「先上传，后关联」模式：
 * 1. 文件先上传到 MinIO，创建 TempFile 记录（associated=false）
 * 2. 业务接口（提交作业、创建资料等）调用 {@link #associateFile(Long)} 标记为已关联
 * 3. 定时任务调用 {@link #cleanupTempFiles()} 清理超过 24 小时未被关联的孤儿文件
 */
@Slf4j
@Service
public class FileService {

    /** 单文件最大 50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 预签名 URL 有效期（分钟） */
    private static final int PRESIGNED_EXPIRY_MINUTES = 30;

    /** 孤儿文件清理阈值（小时） */
    private static final int ORPHAN_CLEANUP_HOURS = 24;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "zip", "rar",
            "png", "jpg", "jpeg", "gif", "mp4"
    );

    /** 可在线预览的扩展名（浏览器原生支持） */
    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "gif"
    );

    /** 头像允许的图片格式 */
    private static final Set<String> AVATAR_ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif"
    );

    /** 头像最大 5MB */
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    /** 头像预签名 URL 有效期（小时） */
    private static final int AVATAR_PRESIGNED_HOURS = 24;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final TempFileRepository tempFileRepository;

    public FileService(MinioClient minioClient,
                       MinioConfig minioConfig,
                       TempFileRepository tempFileRepository) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.tempFileRepository = tempFileRepository;
    }

    // ==================== 上传 ====================

    /**
     * 上传文件到 MinIO，返回文件元数据。
     *
     * @param fileBytes        文件字节
     * @param originalFileName 原始文件名
     * @param contentType      MIME 类型
     * @param uploaderId       上传者用户 ID
     * @return { id, fileName, fileUrl, fileSize }
     */
    @Transactional
    public Map<String, Object> upload(byte[] fileBytes,
                                      String originalFileName,
                                      String contentType,
                                      Long uploaderId) {
        // 1. 校验
        validateFile(fileBytes, originalFileName);

        // 2. 生成 MinIO 对象路径：files/yyyy/MM/uuid.ext
        String extension = extractExtension(originalFileName);
        String objectPath = buildObjectPath(extension);
        String contentTypeOrDefault = contentType != null ? contentType : "application/octet-stream";

        // 3. 上传到 MinIO
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(fileBytes), (long) fileBytes.length, -1L)
                    .contentType(contentTypeOrDefault)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: objectPath={}, size={}", objectPath, fileBytes.length, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试");
        }

        // 4. 创建 TempFile 记录
        TempFile tempFile = TempFile.builder()
                .fileName(originalFileName)
                .fileUrl(objectPath)
                .fileSize((long) fileBytes.length)
                .contentType(contentTypeOrDefault)
                .uploaderId(uploaderId)
                .associated(false)
                .build();
        tempFile = tempFileRepository.save(tempFile);

        log.info("文件上传成功: id={}, name={}, size={}, path={}",
                tempFile.getId(), originalFileName, fileBytes.length, objectPath);

        return Map.of(
                "id", tempFile.getId(),
                "fileName", originalFileName,
                "fileUrl", buildPublicUrl(objectPath),
                "fileSize", fileBytes.length
        );
    }

    // ==================== 头像上传 ====================

    /**
     * 上传用户头像到 MinIO。
     *
     * <p>与通用文件上传不同，头像直接存入 avatars/ 前缀下，
     * 不创建 TempFile 记录，而是返回 objectPath（存入 user.avatar_url）
     * 和 presignedUrl（前端即时展示用，有效期 24 小时）。
     *
     * @param fileBytes        图片字节
     * @param originalFileName 原始文件名（用于提取扩展名）
     * @param userId           用户 ID
     * @return { objectPath, avatarUrl }
     */
    public Map<String, String> uploadAvatar(byte[] fileBytes,
                                             String originalFileName,
                                             Long userId) {
        // 1. 校验
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "头像文件不能为空");
        }
        if (fileBytes.length > MAX_AVATAR_SIZE) {
            throw new BusinessException(400,
                    String.format("头像大小不能超过 %dMB", MAX_AVATAR_SIZE / (1024 * 1024)));
        }

        String extension = extractExtension(originalFileName).toLowerCase();
        if (extension.isEmpty() || !AVATAR_ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400,
                    "头像仅支持: " + String.join(", ", AVATAR_ALLOWED_EXTENSIONS));
        }

        // 2. 生成 MinIO 对象路径：avatars/{userId}_{timestamp}.{ext}
        String objectPath = String.format("avatars/%d_%d.%s",
                userId, System.currentTimeMillis(), extension);

        String contentType = switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };

        // 3. 上传到 MinIO
        try {
            // 删除旧头像（如果存在）
            // 这里不追踪旧路径，新的 objectPath 会覆盖写入
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectPath)
                    .stream(new ByteArrayInputStream(fileBytes), (long) fileBytes.length, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("头像上传 MinIO 失败: userId={}, size={}", userId, fileBytes.length, e);
            throw new BusinessException(500, "头像上传失败，请稍后重试");
        }

        // 4. 生成预签名 URL（24 小时有效）
        String presignedUrl;
        try {
            presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectPath)
                            .method(Http.Method.GET)
                            .expiry(AVATAR_PRESIGNED_HOURS, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("生成头像预签名 URL 失败: path={}", objectPath, e);
            throw new BusinessException(500, "头像链接生成失败");
        }

        log.info("头像上传成功: userId={}, path={}, size={}B", userId, objectPath, fileBytes.length);

        return Map.of(
                "objectPath", objectPath,
                "avatarUrl", presignedUrl
        );
    }

    /**
     * 根据存储的 objectPath 生成头像的预签名访问 URL。
     *
     * @param objectPath 存储在 user.avatar_url 中的 MinIO 对象路径
     * @return 预签名 URL（24 小时有效），如果 objectPath 为空则返回 null
     */
    public String getAvatarPresignedUrl(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectPath)
                            .method(Http.Method.GET)
                            .expiry(AVATAR_PRESIGNED_HOURS, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.warn("生成头像预签名 URL 失败: path={}", objectPath, e);
            return null;
        }
    }

    // ==================== 下载 ====================

    /**
     * 生成 MinIO 预签名下载 URL（有效期 30 分钟）。
     * 浏览器根据 Content-Type 决定下载或预览。
     */
    public String getDownloadUrl(Long fileId) {
        TempFile file = getTempFileOrThrow(fileId);
        return generatePresignedUrl(file.getFileUrl());
    }

    /**
     * 根据 MinIO 对象路径直接生成预签名 URL（绕过 TempFile）。
     * 用于资料等直接存储 objectPath 的场景。
     */
    public String getPresignedUrlByPath(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new BusinessException(400, "文件路径为空");
        }
        return generatePresignedUrl(objectPath);
    }

    // ==================== 原始字节下载 ====================

    /**
     * 从 MinIO 下载文件的原始字节（不通过预签名 URL）。
     * 用于服务端文本提取等场景。
     */
    public byte[] downloadBytes(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new BusinessException(400, "文件路径为空");
        }
        try (var stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectPath)
                        .build())) {
            return stream.readAllBytes();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinIO 下载失败: path={}", objectPath, e);
            throw new BusinessException(500, "文件下载失败");
        }
    }

    // ==================== 预览 ====================

    /**
     * 获取文件预览 URL（有效期 30 分钟）。
     * 仅 PDF 和常见图片格式支持预览，其他格式返回 400 错误。
     * PDF 和图片在浏览器中默认内联显示。
     */
    public String getPreviewUrl(Long fileId) {
        TempFile file = getTempFileOrThrow(fileId);

        String extension = extractExtension(file.getFileName()).toLowerCase();
        if (!PREVIEWABLE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "该文件格式不支持在线预览");
        }

        return generatePresignedUrl(file.getFileUrl());
    }

    // ==================== 文件关联 ====================

    /**
     * 将临时文件标记为已关联（被业务实体引用）。
     * 被关联的文件不会被定时清理任务删除。
     */
    @Transactional
    public void associateFile(Long fileId) {
        TempFile file = getTempFileOrThrow(fileId);
        file.setAssociated(true);
        tempFileRepository.save(file);
    }

    /**
     * 批量关联文件。
     */
    @Transactional
    public void associateFiles(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (Long fileId : fileIds) {
            associateFile(fileId);
        }
    }

    // ==================== 定时清理 ====================

    /**
     * 定时清理超过 24 小时未被关联的孤儿文件。
     * <p>
     * 先逐一从 MinIO 删除对象（单条失败不影响其余），再批量从数据库删除记录。
     */
    public void cleanupTempFiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ORPHAN_CLEANUP_HOURS);
        List<TempFile> orphans = tempFileRepository.findByAssociatedFalseAndCreateTimeBefore(cutoff);

        if (orphans.isEmpty()) {
            log.debug("无过期孤儿文件需要清理");
            return;
        }

        int deletedFromMinio = 0;
        for (TempFile file : orphans) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(file.getFileUrl())
                        .build());
                deletedFromMinio++;
            } catch (Exception e) {
                log.warn("MinIO 删除孤儿文件失败: id={}, path={}", file.getId(), file.getFileUrl(), e);
            }
        }

        List<Long> ids = orphans.stream().map(TempFile::getId).toList();
        int deletedFromDb = tempFileRepository.deleteByIdInAndAssociatedFalse(ids);

        log.info("孤儿文件清理完成: MinIO 删除 {}/{}，数据库删除 {} 条",
                deletedFromMinio, orphans.size(), deletedFromDb);
    }

    // ==================== 内部工具方法 ====================

    /** 获取 TempFile，不存在则抛 404 */
    private TempFile getTempFileOrThrow(Long fileId) {
        return tempFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
    }

    /** 生成 MinIO 预签名 GET URL */
    private String generatePresignedUrl(String objectPath) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectPath)
                            .method(Http.Method.GET)
                            .expiry(PRESIGNED_EXPIRY_MINUTES, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: path={}", objectPath, e);
            throw new BusinessException(500, "文件访问链接生成失败");
        }
    }

    /** 校验文件大小和类型 */
    private void validateFile(byte[] fileBytes, String originalFileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new BusinessException(400,
                    String.format("文件大小不能超过 %dMB", MAX_FILE_SIZE / (1024 * 1024)));
        }
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        String extension = extractExtension(originalFileName).toLowerCase();
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400,
                    "不支持的文件格式: ." + extension + "（支持: " + String.join(", ", ALLOWED_EXTENSIONS) + "）");
        }
    }

    /** 提取文件扩展名（不含点号） */
    static String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }

    /** 构建 MinIO 对象路径：files/yyyy/MM/uuid.ext */
    private static String buildObjectPath(String extension) {
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return String.format("files/%s/%s.%s", datePath, UUID.randomUUID(), extension);
    }

    /** 构建供前端使用的公开 URL 路径 */
    private static String buildPublicUrl(String objectPath) {
        return "/api/files/" + objectPath;
    }
}
