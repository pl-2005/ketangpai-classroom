package com.ketangpai.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 临时文件表 — 「先上传，后关联」模式
 * <p>
 * 文件上传后先记录在此表（associated=false），业务接口（提交作业、创建资料等）传入 fileId 后
 * 将记录标记为 associated=true。定时任务清理超过 24 小时未被关联的孤儿文件。
 */
@Entity
@Table(name = "temp_file")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempFile extends BaseEntity {

    /** 原始文件名 */
    @Column(nullable = false, length = 255)
    private String fileName;

    /** MinIO 对象路径（如 files/2026/06/uuid.pdf） */
    @Column(nullable = false, length = 500)
    private String fileUrl;

    /** 文件大小（字节） */
    @Column(nullable = false)
    private Long fileSize;

    /** MIME 类型 */
    @Column(length = 100)
    private String contentType;

    /** 上传者用户 ID */
    @Column(nullable = false)
    private Long uploaderId;

    /** 是否已被业务实体关联 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean associated = false;
}
