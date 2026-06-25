-- =============================================
-- 课堂派 - 数据库初始化脚本
-- 版本：v1.1  日期：2026-06-23
-- =============================================

CREATE DATABASE IF NOT EXISTS ketangpai
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ketangpai;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像 URL (MinIO)',
    `role` ENUM('TEACHER','STUDENT') NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号启用状态',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 2. 课程表
-- =============================================
CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `description` TEXT DEFAULT NULL COMMENT '课程简介',
    `course_code` VARCHAR(20) NOT NULL COMMENT '课程号（学生加入用）',
    `cover_url` VARCHAR(255) DEFAULT NULL COMMENT '封面图 URL (MinIO)',
    `status` ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `creator_id` BIGINT NOT NULL COMMENT '创建者 user_id',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_code` (`course_code`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 3. 课程成员表
-- =============================================
CREATE TABLE IF NOT EXISTS `course_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` ENUM('CREATOR','TEACHER','STUDENT') NOT NULL COMMENT '成员角色',
    `is_archived` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否归档该课程（可恢复）',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '退课软删除（退课后可重新加入）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '卡片排序（拖拽排序）',
    `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_user` (`course_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 4. 作业表
-- =============================================
CREATE TABLE IF NOT EXISTS `assignment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '所属课程',
    `title` VARCHAR(200) NOT NULL COMMENT '作业标题',
    `content` TEXT DEFAULT NULL COMMENT '作业描述（富文本）',
    `status` ENUM('DRAFT','PUBLISHED','CLOSED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    `deadline` DATETIME DEFAULT NULL COMMENT '截止时间',
    `max_score` INT NOT NULL DEFAULT 100 COMMENT '满分',
    `allow_resubmit` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许重复提交',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_status` (`status`),
    KEY `idx_course_deadline` (`course_id`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 5. 作业附件表
-- =============================================
CREATE TABLE IF NOT EXISTS `assignment_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL COMMENT '所属作业',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT 'MinIO 存储 URL',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 6. 作业提交表
-- =============================================
CREATE TABLE IF NOT EXISTS `submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL COMMENT '所属作业',
    `student_id` BIGINT NOT NULL COMMENT '提交学生 user_id',
    `content` TEXT DEFAULT NULL COMMENT '文本内容',
    `status` ENUM('DRAFT','SUBMITTED','GRADED','RETURNED') NOT NULL DEFAULT 'DRAFT' COMMENT '提交状态',
    `score` INT DEFAULT NULL COMMENT '教师最终评分',
    `teacher_comment` TEXT DEFAULT NULL COMMENT '教师评语',
    `version` INT NOT NULL DEFAULT 1 COMMENT '提交版本号',
    `submitted_at` DATETIME DEFAULT NULL COMMENT '提交时间',
    `graded_at` DATETIME DEFAULT NULL COMMENT '批阅时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_student` (`assignment_id`, `student_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 7. 提交文件表
-- =============================================
CREATE TABLE IF NOT EXISTS `submission_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id` BIGINT NOT NULL COMMENT '所属提交',
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT 'MinIO 存储 URL',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 8. 资料文件夹表
-- =============================================
CREATE TABLE IF NOT EXISTS `material_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '所属课程',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父文件夹 ID (NULL=根目录)',
    `name` VARCHAR(100) NOT NULL COMMENT '文件夹名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 9. 课程资料表
-- =============================================
CREATE TABLE IF NOT EXISTS `material` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '所属课程',
    `folder_id` BIGINT DEFAULT NULL COMMENT '所属文件夹 (NULL=根目录)',
    `title` VARCHAR(200) NOT NULL COMMENT '资料名称',
    `type` ENUM('FILE','LINK') NOT NULL COMMENT '资料类型',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件存储 URL (type=FILE)',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小 (type=FILE)',
    `link_url` VARCHAR(500) DEFAULT NULL COMMENT '外链 URL (type=LINK)',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_folder_id` (`folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 10. 话题表
-- =============================================
CREATE TABLE IF NOT EXISTS `topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '所属课程',
    `author_id` BIGINT NOT NULL COMMENT '作者 user_id',
    `title` VARCHAR(200) NOT NULL COMMENT '话题标题',
    `content` TEXT NOT NULL COMMENT '话题内容',
    `status` ENUM('NORMAL','PINNED','LOCKED') NOT NULL DEFAULT 'NORMAL' COMMENT '状态',
    `is_anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否匿名',
    `discussion_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '教师可关闭讨论',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 11. 话题回复表
-- =============================================
CREATE TABLE IF NOT EXISTS `topic_reply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `topic_id` BIGINT NOT NULL COMMENT '所属话题',
    `author_id` BIGINT NOT NULL COMMENT '回复者 user_id',
    `content` TEXT NOT NULL COMMENT '回复内容',
    `is_anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否匿名',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父回复 ID（楼中楼）',
    `path` VARCHAR(500) DEFAULT NULL COMMENT '物化路径，如 /1/3/5，高效排序楼中楼',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_topic_id` (`topic_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 12. 备课区表
-- =============================================
CREATE TABLE IF NOT EXISTS `lesson_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '所属教师',
    `type` ENUM('ASSIGNMENT','MATERIAL','TOPIC') NOT NULL COMMENT '草稿类型',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '草稿标题',
    `content_json` JSON DEFAULT NULL COMMENT '草稿内容（结构化 JSON）',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 13. AI 批阅配置表
-- =============================================
CREATE TABLE IF NOT EXISTS `ai_grading_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL COMMENT '关联作业（一对一）',
    `prompt_template` TEXT DEFAULT NULL COMMENT '自定义 Prompt 模板',
    `rubric_json` JSON DEFAULT NULL COMMENT '评分标准（维度+权重+期望答案）',
    `grading_style` ENUM('STRICT','BALANCED','ENCOURAGING','CONCISE') NOT NULL DEFAULT 'BALANCED' COMMENT '批阅风格',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 AI 批阅',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 14. AI 批阅结果表
-- =============================================
CREATE TABLE IF NOT EXISTS `ai_grading_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id` BIGINT NOT NULL COMMENT '关联提交',
    `score` INT DEFAULT NULL COMMENT 'AI 预评分',
    `comment` TEXT DEFAULT NULL COMMENT 'AI 评语',
    `suggestions` TEXT DEFAULT NULL COMMENT '改进建议',
    `detail_json` JSON DEFAULT NULL COMMENT '各维度详细评分',
    `graded_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '批阅时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 15. 相似度报告表
-- =============================================
CREATE TABLE IF NOT EXISTS `similarity_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL COMMENT '关联作业',
    `total_submissions` INT NOT NULL COMMENT '参与比对的提交数',
    `threshold` DECIMAL(3,2) NOT NULL DEFAULT 0.80 COMMENT '相似度阈值',
    `generated_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 16. 相似度对表
-- =============================================
CREATE TABLE IF NOT EXISTS `similarity_pair` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL COMMENT '关联报告',
    `submission_a_id` BIGINT NOT NULL COMMENT '提交 A',
    `submission_b_id` BIGINT NOT NULL COMMENT '提交 B',
    `similarity_score` DECIMAL(5,4) NOT NULL COMMENT '余弦相似度 (0~1)',
    `highlighted_segments` JSON DEFAULT NULL COMMENT '相似段落高亮信息',
    PRIMARY KEY (`id`),
    KEY `idx_report_id` (`report_id`),
    KEY `idx_score` (`similarity_score` DESC),
    KEY `idx_submission_a` (`submission_a_id`),
    KEY `idx_submission_b` (`submission_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 17. 通知表
-- =============================================
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '接收通知的用户',
    `course_id` BIGINT DEFAULT NULL COMMENT '关联课程',
    `type` ENUM(
        'ASSIGNMENT_PUBLISHED','ASSIGNMENT_URGED','ASSIGNMENT_GRADED',
        'ASSIGNMENT_RETURNED','TOPIC_REPLY','COURSE_JOINED','COURSE_ANNOUNCEMENT',
        'AI_GRADED'
    ) NOT NULL COMMENT '通知类型',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT DEFAULT NULL COMMENT '通知正文',
    `related_id` BIGINT DEFAULT NULL COMMENT '关联实体 ID（如 assignment_id）',
    `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已读状态',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除（用户手动删除通知）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 21. AI 批阅批量任务表
-- =============================================
CREATE TABLE IF NOT EXISTS `grading_batch_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL COMMENT '作业 ID',
    `teacher_id` BIGINT NOT NULL COMMENT '触发教师',
    `status` ENUM('PENDING','IN_PROGRESS','COMPLETED','PARTIALLY_FAILED','FAILED') NOT NULL DEFAULT 'PENDING',
    `total_count` INT NOT NULL DEFAULT 0,
    `completed_count` INT NOT NULL DEFAULT 0,
    `failed_count` INT NOT NULL DEFAULT 0,
    `error_message` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 18. 知识库文档块表（RAG）
-- =============================================
CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT '所属课程',
    `source_type` ENUM('MATERIAL','ASSIGNMENT','TOPIC') NOT NULL COMMENT '来源类型',
    `source_id` BIGINT NOT NULL COMMENT '来源实体 ID',
    `source_name` VARCHAR(200) DEFAULT NULL COMMENT '来源名称（便于展示引用）',
    `chunk_index` INT NOT NULL COMMENT '块序号',
    `content` TEXT NOT NULL COMMENT '文本内容',
    `qdrant_point_id` VARCHAR(100) DEFAULT NULL COMMENT 'Qdrant 中对应的 point UUID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 19. AI 答疑对话表
-- =============================================
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '提问学生',
    `course_id` BIGINT NOT NULL COMMENT '所在课程',
    `session_id` VARCHAR(36) DEFAULT NULL COMMENT '会话标识（UUID），区分同一课程内多个独立对话',
    `role` ENUM('USER','ASSISTANT') NOT NULL COMMENT '消息角色',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `references_json` JSON DEFAULT NULL COMMENT '引用来源（仅 ASSISTANT）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_course` (`user_id`, `course_id`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- 20. 临时文件表（先上传，后关联）
-- =============================================
CREATE TABLE IF NOT EXISTS `temp_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT 'MinIO 对象路径',
    `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `content_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
    `uploader_id` BIGINT NOT NULL COMMENT '上传者用户 ID',
    `associated` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已被业务实体关联',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_uploader_id` (`uploader_id`),
    KEY `idx_associated_expired` (`associated`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
