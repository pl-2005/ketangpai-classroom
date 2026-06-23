package com.ketangpai.service;

import com.ketangpai.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 文件上传管理服务
 * <p>
 * 采用「先上传，后关联」模式：
 * 1. 文件先上传到 MinIO，获得临时 ID
 * 2. 业务接口（提交作业、创建资料等）传入 fileId 建立关联
 * 3. 定时任务清理超过 24 小时未被关联的孤儿文件
 */
@Service
@RequiredArgsConstructor
public class FileService {

    // TODO: 注入 MinioClient 和临时文件 Repository

    /** 上传文件到 MinIO，返回文件信息 */
    public Map<String, Object> upload(byte[] fileBytes, String originalFileName, String contentType) {
        // TODO: 实际 MinIO 上传逻辑
        //   1. 生成存储路径：files/{yyyy}/{MM}/{uuid}.{ext}
        //   2. 校验文件大小（≤ 50MB）和类型
        //   3. 上传到 MinIO
        //   4. 返回 fileId、fileUrl、fileName、fileSize

        return Map.of(
                "id", 0L,
                "fileName", originalFileName,
                "fileUrl", "http://localhost:9000/ketangpai/files/temp/" + originalFileName,
                "fileSize", fileBytes.length
        );
    }

    /** 生成 MinIO 预签名下载 URL（有效期 30 分钟） */
    public String getDownloadUrl(Long fileId) {
        // TODO: 生成 MinIO 预签名 URL，302 重定向
        throw new BusinessException(500, "文件下载服务待实现");
    }

    /** 获取文件预览 URL */
    public String getPreviewUrl(Long fileId) {
        // TODO: 对 PDF、图片等可预览格式返回在线预览 URL
        throw new BusinessException(500, "文件预览服务待实现");
    }

    /** 定时清理超过 24 小时未被关联的临时文件 */
    public void cleanupTempFiles() {
        // TODO: 查询创建时间超过 24 小时且未被关联的临时文件，从 MinIO 删除
    }
}
