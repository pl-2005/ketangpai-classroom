-- =============================================
-- 课堂派 - 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS ketangpai
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ketangpai;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `nickname` VARCHAR(50) DEFAULT NULL,
    `role` ENUM('TEACHER', 'STUDENT') NOT NULL,
    `avatar` VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 课程表
CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_num` VARCHAR(20) NOT NULL COMMENT '课程编号',
    `course_name` VARCHAR(100) NOT NULL COMMENT '课程名称',
    `description` TEXT COMMENT '课程描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:归档 1:启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_num` (`course_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 课程-用户关联表
CREATE TABLE IF NOT EXISTS `course_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` ENUM('CREATOR', 'TEACHER', 'STUDENT') NOT NULL,
    `is_archived` TINYINT NOT NULL DEFAULT 0 COMMENT '0:未归档 1:已归档',
    `sort_order` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_user` (`course_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 作业表
CREATE TABLE IF NOT EXISTS `assignment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT COMMENT '作业描述',
    `attachments` JSON DEFAULT NULL COMMENT '附件列表',
    `deadline` DATETIME DEFAULT NULL,
    `max_score` INT DEFAULT 100,
    `ai_grading_enabled` TINYINT NOT NULL DEFAULT 0,
    `ai_grading_prompt` TEXT COMMENT 'AI批阅提示词',
    `ai_grading_rubric` JSON DEFAULT NULL COMMENT 'AI批阅评分标准',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0:草稿 1:已发布',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 作业提交表
CREATE TABLE IF NOT EXISTS `submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `content` TEXT COMMENT '提交内容',
    `files` JSON DEFAULT NULL COMMENT '提交文件列表',
    `version` INT NOT NULL DEFAULT 1,
    `score` INT DEFAULT NULL,
    `ai_score` INT DEFAULT NULL COMMENT 'AI预评分',
    `ai_comment` TEXT COMMENT 'AI评语',
    `teacher_comment` TEXT,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0:已提交 1:已批阅 2:已催交',
    `submit_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `grade_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 资料表
CREATE TABLE IF NOT EXISTS `resource` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `type` ENUM('FILE', 'LINK') NOT NULL,
    `url` VARCHAR(500) DEFAULT NULL COMMENT '文件URL或外链URL',
    `size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父文件夹ID',
    `is_folder` TINYINT NOT NULL DEFAULT 0,
    `sort_order` INT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 话题表
CREATE TABLE IF NOT EXISTS `topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `is_pinned` TINYINT NOT NULL DEFAULT 0,
    `is_anonymous` TINYINT NOT NULL DEFAULT 0,
    `discussion_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '教师可关闭',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 话题回复表
CREATE TABLE IF NOT EXISTS `topic_reply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `topic_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `is_anonymous` TINYINT NOT NULL DEFAULT 0,
    `parent_id` BIGINT DEFAULT NULL COMMENT '父回复ID(楼中楼)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 通知表
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `course_id` BIGINT DEFAULT NULL,
    `type` ENUM('ASSIGNMENT_PUBLISHED', 'ASSIGNMENT_GRADED', 'ASSIGNMENT_URGED', 'TOPIC_REPLY', 'COURSE_JOINED') NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT,
    `is_read` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 备课区表
CREATE TABLE IF NOT EXISTS `preparation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `type` ENUM('ASSIGNMENT', 'RESOURCE', 'TOPIC') NOT NULL,
    `title` VARCHAR(200) DEFAULT NULL,
    `content` JSON DEFAULT NULL COMMENT '备课内容(JSON)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
