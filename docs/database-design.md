# 课堂派 — 数据库设计文档

> 版本：v1.2  
> 日期：2026-06-26  
> 数据库：MySQL 8.4 (InnoDB, utf8mb4)

---

## 一、设计原则

| 原则 | 说明 |
|------|------|
| **命名规范** | 表名小写下划线；主键统一 `id`；时间字段 `create_time` / `update_time` |
| **字符集** | `utf8mb4` + `utf8mb4_unicode_ci`，支持 emoji |
| **引擎** | InnoDB（行锁 + 事务） |
| **软删除** | 核心业务表使用 `deleted` 标记，不物理删除 |
| **JSON 字段** | 附件列表、评分标准等半结构化数据使用 JSON 类型 |
| **外键** | 应用层维护关系，数据库层仅建索引，避免级联删除误操作 |

---

## 二、ER 图（核心实体关系）

```
 ┌──────────┐       ┌──────────────────┐       ┌──────────┐
 │   user   │       │  course_member   │       │  course  │
 ├──────────┤       ├──────────────────┤       ├──────────┤
 │ id (PK)  │──1:N──│ user_id  (FK)    │──N:1──│ id (PK)  │
 │ username │       │ course_id (FK)   │       │ name     │
 │ password │       │ role             │       │ code     │
 │ role     │       │ sort_order       │       │ status   │
 │ ...      │       │ is_archived      │       │ ...      │
 └──────────┘       │ deleted          │       └──────────┘
      │             └──────────────────┘              │
      │ 1:N                                          │ 1:N
      ▼                                              ▼
 ┌──────────────┐                            ┌──────────────┐
 │  submission  │                            │  assignment  │
 ├──────────────┤                            ├──────────────┤
 │ id (PK)      │──N:1── assignment_id (FK) ──│ id (PK)      │
 │ student_id   │                            │ course_id    │
 │ content      │                            │ title        │
 │ score        │                            │ status       │
 │ status       │                            │ ...          │
 │ version      │                            └──────────────┘
 └──────────────┘                                   │
      │ 1:N                                         │ 1:1
      ▼                                             ▼
 ┌────────────────┐                        ┌──────────────────┐
 │ submission_file│                        │ ai_grading_config│
 └────────────────┘                        └──────────────────┘
                                                   │
 ┌──────────────┐       ┌──────────────┐            │ 1:N
 │    topic     │       │   material   │            ▼
 ├──────────────┤       ├──────────────┤   ┌──────────────────┐
 │ id (PK)      │       │ id (PK)      │   │ ai_grading_result│
 │ course_id    │       │ course_id    │   └──────────────────┘
 │ author_id    │       │ folder_id    │
 │ ...          │       │ type         │   ┌──────────────────┐
 └──────────────┘       │ ...          │   │similarity_report │
      │ 1:N             └──────────────┘   ├──────────────────┤
      ▼                       │            │ assignment_id    │
 ┌──────────────┐             │ 1:N        │ threshold        │
 │  topic_reply │             ▼            └──────────────────┘
 └──────────────┘    ┌────────────────┐          │ 1:N
                     │material_folder │          ▼
 ┌──────────────┐    └────────────────┘   ┌──────────────────┐
 │ notification │                         │ similarity_pair  │
 └──────────────┘                         └──────────────────┘

 ┌────────────────┐    ┌──────────────────────┐
 │ lesson_draft   │    │ knowledge_chunk      │
 └────────────────┘    └──────────────────────┘

 ┌────────────────┐
 │  chat_message  │  (AI 答疑对话历史)
 └────────────────┘
```

---

## 三、枚举定义

```java
// com.ketangpai.model.enums.UserRole
public enum UserRole {
    TEACHER,  // 教师
    STUDENT   // 学生
}

// com.ketangpai.model.enums.CourseStatus
public enum CourseStatus {
    ACTIVE,    // 进行中
    ARCHIVED   // 已归档
}

// com.ketangpai.model.enums.CourseMemberRole
public enum CourseMemberRole {
    CREATOR,   // 创建者
    TEACHER,   // 教师（多人共管）
    STUDENT    // 学生
}

// com.ketangpai.model.enums.AssignmentStatus
public enum AssignmentStatus {
    DRAFT,     // 草稿
    PUBLISHED, // 已发布
    CLOSED     // 已关闭
}

// com.ketangpai.model.enums.SubmissionStatus
public enum SubmissionStatus {
    DRAFT,     // 草稿（学生未提交）
    SUBMITTED, // 已提交
    GRADED,    // 已批阅
    RETURNED   // 已退回（允许修改重交）
}

// com.ketangpai.model.enums.MaterialType
public enum MaterialType {
    FILE,      // 上传文件
    LINK       // 外链资源
}

// com.ketangpai.model.enums.TopicStatus
public enum TopicStatus {
    NORMAL,    // 正常
    PINNED,    // 置顶
    LOCKED     // 锁定（禁止回复）
}

// com.ketangpai.model.enums.DraftType
public enum DraftType {
    ASSIGNMENT, // 作业草稿
    MATERIAL,   // 资料草稿
    TOPIC       // 话题草稿
}

// com.ketangpai.model.enums.NotificationType
public enum NotificationType {
    ASSIGNMENT_PUBLISHED,  // 作业发布
    ASSIGNMENT_URGED,      // 催交提醒
    ASSIGNMENT_GRADED,     // 批阅完成
    ASSIGNMENT_RETURNED,   // 作业退回
    TOPIC_REPLY,           // 话题回复
    COURSE_JOINED,         // 新成员加入
    COURSE_ANNOUNCEMENT    // 课程公告
}

// com.ketangpai.model.enums.ChatRole
public enum ChatRole {
    USER,       // 学生提问
    ASSISTANT   // AI 回答
}

// com.ketangpai.model.enums.GradingStyle
public enum GradingStyle {
    STRICT,      // 严厉
    BALANCED,    // 平衡（默认）
    ENCOURAGING, // 鼓励
    CONCISE      // 简洁
}

// com.ketangpai.model.enums.SourceType
public enum SourceType {
    MATERIAL,   // 课程资料
    ASSIGNMENT, // 作业
    TOPIC       // 话题
}
```

---

## 四、表结构详细设计

### 4.1 用户表 `user`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 用户 ID |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | 登录用户名 |
| `password` | VARCHAR(255) | NOT NULL | BCrypt 加密 |
| `email` | VARCHAR(100) | UNIQUE | 邮箱（用于找回密码） |
| `real_name` | VARCHAR(50) | DEFAULT NULL | 真实姓名 |
| `avatar_url` | VARCHAR(255) | DEFAULT NULL | 头像 URL (MinIO) |
| `role` | ENUM('TEACHER','STUDENT') | NOT NULL | 角色 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 账号启用状态 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除标记 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 注册时间 |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引：** `uk_username` (UNIQUE), `uk_email` (UNIQUE)

**DDL：**
```sql
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `real_name` VARCHAR(50) DEFAULT NULL,
    `avatar_url` VARCHAR(255) DEFAULT NULL,
    `role` ENUM('TEACHER','STUDENT') NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.2 课程表 `course`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 课程 ID |
| `name` | VARCHAR(100) | NOT NULL | 课程名称 |
| `description` | TEXT | DEFAULT NULL | 课程简介 |
| `course_code` | VARCHAR(20) | NOT NULL, UNIQUE | 课程号（学生加入用） |
| `cover_url` | VARCHAR(255) | DEFAULT NULL | 封面图 URL (MinIO) |
| `status` | ENUM('ACTIVE','ARCHIVED') | NOT NULL DEFAULT 'ACTIVE' | 状态 |
| `creator_id` | BIGINT | NOT NULL | 创建者 user_id |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除标记 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引：** `uk_course_code` (UNIQUE), `idx_creator_id`, `idx_status`

**DDL：**
```sql
CREATE TABLE `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `course_code` VARCHAR(20) NOT NULL,
    `cover_url` VARCHAR(255) DEFAULT NULL,
    `status` ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    `creator_id` BIGINT NOT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_code` (`course_code`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.3 课程成员表 `course_member`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 课程 ID |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `role` | ENUM('CREATOR','TEACHER','STUDENT') | NOT NULL | 成员角色 |
| `is_archived` | TINYINT(1) | NOT NULL DEFAULT 0 | 用户是否归档该课程（可恢复） |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 退课软删除标记（退课后可重新加入） |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 卡片排序（拖拽排序） |
| `joined_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | 加入时间 |

**索引：** `uk_course_user` (UNIQUE, course_id + user_id), `idx_user_id`

**语义说明：**
- **归档** (`is_archived=1`)：用户在课程列表中隐藏该课程卡片，可随时恢复查看
- **退课** (`deleted=1`)：学生主动退出课程，软删除该记录。重新加入时，将 `deleted` 置回 0 并更新 `joined_at`。`uk_course_user` 唯一约束涵盖 (course_id, user_id)，同一条记录复用，不产生重复数据

**DDL：**
```sql
CREATE TABLE `course_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role` ENUM('CREATOR','TEACHER','STUDENT') NOT NULL,
    `is_archived` TINYINT(1) NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `sort_order` INT NOT NULL DEFAULT 0,
    `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_user` (`course_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.4 作业表 `assignment`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 所属课程 |
| `title` | VARCHAR(200) | NOT NULL | 作业标题 |
| `content` | TEXT | DEFAULT NULL | 作业描述（富文本） |
| `status` | ENUM('DRAFT','PUBLISHED','CLOSED') | NOT NULL DEFAULT 'DRAFT' | |
| `deadline` | DATETIME | DEFAULT NULL | 截止时间 |
| `max_score` | INT | NOT NULL DEFAULT 100 | 满分 |
| `allow_resubmit` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否允许重复提交 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `idx_course_id`, `idx_status`, `idx_course_deadline` (course_id + deadline，按截止时间排列作业)

**DDL：**
```sql
CREATE TABLE `assignment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT DEFAULT NULL,
    `status` ENUM('DRAFT','PUBLISHED','CLOSED') NOT NULL DEFAULT 'DRAFT',
    `deadline` DATETIME DEFAULT NULL,
    `max_score` INT NOT NULL DEFAULT 100,
    `allow_resubmit` TINYINT(1) NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_status` (`status`),
    KEY `idx_course_deadline` (`course_id`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.5 作业附件表 `assignment_attachment`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `assignment_id` | BIGINT | NOT NULL | 所属作业 |
| `file_name` | VARCHAR(255) | NOT NULL | 原始文件名 |
| `file_url` | VARCHAR(500) | NOT NULL | MinIO 存储 URL |
| `file_size` | BIGINT | NOT NULL DEFAULT 0 | 文件大小（字节） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_assignment_id`

说明：独立附件表替代 JSON 字段，方便按文件类型筛选和管理。

```sql
CREATE TABLE `assignment_attachment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_url` VARCHAR(500) NOT NULL,
    `file_size` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.6 作业提交表 `submission`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `assignment_id` | BIGINT | NOT NULL | 所属作业 |
| `student_id` | BIGINT | NOT NULL | 提交学生 user_id |
| `content` | TEXT | DEFAULT NULL | 文本内容 |
| `status` | ENUM('DRAFT','SUBMITTED','GRADED','RETURNED') | NOT NULL DEFAULT 'DRAFT' | |
| `score` | INT | DEFAULT NULL | 教师最终评分 |
| `teacher_comment` | TEXT | DEFAULT NULL | 教师评语 |
| `version` | INT | NOT NULL DEFAULT 1 | 提交版本号 |
| `submitted_at` | DATETIME | DEFAULT NULL | 提交时间 |
| `graded_at` | DATETIME | DEFAULT NULL | 批阅时间 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `uk_assignment_student` (UNIQUE, assignment_id + student_id), `idx_student_id`

说明：`(assignment_id, student_id)` 唯一约束保证每个学生对每个作业只有一条提交记录，版本覆盖更新。

```sql
CREATE TABLE `submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL,
    `student_id` BIGINT NOT NULL,
    `content` TEXT DEFAULT NULL,
    `status` ENUM('DRAFT','SUBMITTED','GRADED','RETURNED') NOT NULL DEFAULT 'DRAFT',
    `score` INT DEFAULT NULL,
    `teacher_comment` TEXT DEFAULT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `submitted_at` DATETIME DEFAULT NULL,
    `graded_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_student` (`assignment_id`, `student_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.7 提交文件表 `submission_file`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `submission_id` | BIGINT | NOT NULL | 所属提交 |
| `file_name` | VARCHAR(255) | NOT NULL | 原始文件名 |
| `file_url` | VARCHAR(500) | NOT NULL | MinIO 存储 URL |
| `file_size` | BIGINT | NOT NULL DEFAULT 0 | 文件大小（字节） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_submission_id`

```sql
CREATE TABLE `submission_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id` BIGINT NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_url` VARCHAR(500) NOT NULL,
    `file_size` BIGINT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.8 资料文件夹表 `material_folder`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 所属课程 |
| `parent_id` | BIGINT | DEFAULT NULL | 父文件夹 ID (NULL = 根目录) |
| `name` | VARCHAR(100) | NOT NULL | 文件夹名称 |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 排序 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `idx_course_id`, `idx_parent_id`

```sql
CREATE TABLE `material_folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `parent_id` BIGINT DEFAULT NULL,
    `name` VARCHAR(100) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.9 课程资料表 `material`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 所属课程 |
| `folder_id` | BIGINT | DEFAULT NULL | 所属文件夹 (NULL = 根目录) |
| `title` | VARCHAR(200) | NOT NULL | 资料名称 |
| `type` | ENUM('FILE','LINK') | NOT NULL | 资料类型 |
| `file_url` | VARCHAR(500) | DEFAULT NULL | 文件存储 URL (type=FILE) |
| `file_size` | BIGINT | DEFAULT NULL | 文件大小 (type=FILE) |
| `link_url` | VARCHAR(500) | DEFAULT NULL | 外链 URL (type=LINK) |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 排序 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `idx_course_id`, `idx_folder_id`

```sql
CREATE TABLE `material` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `folder_id` BIGINT DEFAULT NULL,
    `title` VARCHAR(200) NOT NULL,
    `type` ENUM('FILE','LINK') NOT NULL,
    `file_url` VARCHAR(500) DEFAULT NULL,
    `file_size` BIGINT DEFAULT NULL,
    `link_url` VARCHAR(500) DEFAULT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_folder_id` (`folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.10 话题表 `topic`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 所属课程 |
| `author_id` | BIGINT | NOT NULL | 作者 user_id |
| `title` | VARCHAR(200) | NOT NULL | 话题标题 |
| `content` | TEXT | NOT NULL | 话题内容 |
| `status` | ENUM('NORMAL','PINNED','LOCKED') | NOT NULL DEFAULT 'NORMAL' | |
| `is_anonymous` | TINYINT(1) | NOT NULL DEFAULT 0 | 是否匿名 |
| `discussion_enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 教师可关闭讨论 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `idx_course_id`, `idx_author_id`

```sql
CREATE TABLE `topic` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT NOT NULL,
    `status` ENUM('NORMAL','PINNED','LOCKED') NOT NULL DEFAULT 'NORMAL',
    `is_anonymous` TINYINT(1) NOT NULL DEFAULT 0,
    `discussion_enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.11 话题回复表 `topic_reply`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `topic_id` | BIGINT | NOT NULL | 所属话题 |
| `author_id` | BIGINT | NOT NULL | 回复者 user_id |
| `content` | TEXT | NOT NULL | 回复内容 |
| `is_anonymous` | TINYINT(1) | NOT NULL DEFAULT 0 | |
| `parent_id` | BIGINT | DEFAULT NULL | 父回复 ID（楼中楼） |
| `path` | VARCHAR(500) | DEFAULT NULL | 物化路径，如 `/1/3/5`，用于高效排序楼中楼 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_topic_id`, `idx_parent_id`, `idx_path`

```sql
CREATE TABLE `topic_reply` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `topic_id` BIGINT NOT NULL,
    `author_id` BIGINT NOT NULL,
    `content` TEXT NOT NULL,
    `is_anonymous` TINYINT(1) NOT NULL DEFAULT 0,
    `parent_id` BIGINT DEFAULT NULL,
    `path` VARCHAR(500) DEFAULT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_topic_id` (`topic_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.12 备课区表 `lesson_draft`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `user_id` | BIGINT | NOT NULL | 所属教师 |
| `type` | ENUM('ASSIGNMENT','MATERIAL','TOPIC') | NOT NULL | 草稿类型 |
| `title` | VARCHAR(200) | DEFAULT NULL | 草稿标题 |
| `content_json` | JSON | DEFAULT NULL | 草稿内容（结构化 JSON） |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `idx_user_id`, `idx_type`

说明：`content_json` 存储不同类型草稿的完整数据，教师发布时反序列化后一键导入到对应表。

```sql
CREATE TABLE `lesson_draft` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `type` ENUM('ASSIGNMENT','MATERIAL','TOPIC') NOT NULL,
    `title` VARCHAR(200) DEFAULT NULL,
    `content_json` JSON DEFAULT NULL,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.13 AI 批阅配置表 `ai_grading_config`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `assignment_id` | BIGINT | NOT NULL, UNIQUE | 关联作业（一对一） |
| `prompt_template` | TEXT | DEFAULT NULL | 自定义 Prompt 模板 |
| `rubric_json` | JSON | DEFAULT NULL | 评分标准（维度+权重+期望答案） |
| `grading_style` | ENUM('STRICT','BALANCED','ENCOURAGING','CONCISE') | NOT NULL DEFAULT 'BALANCED' | 批阅风格 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 0 | 是否启用 AI 批阅 |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |
| `update_time` | DATETIME | ON UPDATE CURRENT_TIMESTAMP | |

**索引：** `uk_assignment_id` (UNIQUE)

```sql
CREATE TABLE `ai_grading_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL,
    `prompt_template` TEXT DEFAULT NULL,
    `rubric_json` JSON DEFAULT NULL,
    `grading_style` ENUM('STRICT','BALANCED','ENCOURAGING','CONCISE') NOT NULL DEFAULT 'BALANCED',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`rubric_json` 结构示例：
```json
[
  {
    "dimension": "内容完整性",
    "weight": 30,
    "maxScore": 30,
    "criteria": "是否完整回答了所有问题要点"
  },
  {
    "dimension": "逻辑结构",
    "weight": 25,
    "maxScore": 25,
    "criteria": "论述是否层次分明、逻辑清晰"
  }
]
```

---

### 4.14 AI 批阅结果表 `ai_grading_result`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `submission_id` | BIGINT | NOT NULL | 关联提交 |
| `score` | INT | DEFAULT NULL | AI 预评分 |
| `comment` | TEXT | DEFAULT NULL | AI 评语 |
| `suggestions` | TEXT | DEFAULT NULL | 改进建议 |
| `detail_json` | JSON | DEFAULT NULL | 各维度详细评分 |
| `graded_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `uk_submission_id` (UNIQUE，每次提交只生成一次 AI 结果)

```sql
CREATE TABLE `ai_grading_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `submission_id` BIGINT NOT NULL,
    `score` INT DEFAULT NULL,
    `comment` TEXT DEFAULT NULL,
    `suggestions` TEXT DEFAULT NULL,
    `detail_json` JSON DEFAULT NULL,
    `graded_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.15 相似度报告表 `similarity_report`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `assignment_id` | BIGINT | NOT NULL | 关联作业 |
| `total_submissions` | INT | NOT NULL | 参与比对的提交数 |
| `threshold` | DECIMAL(3,2) | NOT NULL DEFAULT 0.80 | 相似度阈值 |
| `suspicious_count` | INT | NOT NULL DEFAULT 0 | 疑似抄袭对数（超阈值对数量） |
| `generated_at` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_assignment_id`

说明：每次教师触发相似度分析，生成一份报告 + 多对相似对。

```sql
CREATE TABLE `similarity_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `assignment_id` BIGINT NOT NULL,
    `total_submissions` INT NOT NULL,
    `threshold` DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    `suspicious_count` INT NOT NULL DEFAULT 0 COMMENT '疑似抄袭对数',
    `generated_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_assignment_id` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.16 相似度对表 `similarity_pair`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `report_id` | BIGINT | NOT NULL | 关联报告 |
| `submission_a_id` | BIGINT | NOT NULL | 提交 A |
| `submission_b_id` | BIGINT | NOT NULL | 提交 B |
| `similarity_score` | DECIMAL(5,4) | NOT NULL | 余弦相似度 (0~1) |
| `highlighted_segments` | JSON | DEFAULT NULL | 相似段落高亮信息 |

**索引：** `idx_report_id`, `idx_score` (按相似度降序检索), `idx_submission_a`, `idx_submission_b`

说明：`idx_submission_a` 和 `idx_submission_b` 支持"查询某份提交和谁相似"（跨作业批次对比），避免单表扫描。

```sql
CREATE TABLE `similarity_pair` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `report_id` BIGINT NOT NULL,
    `submission_a_id` BIGINT NOT NULL,
    `submission_b_id` BIGINT NOT NULL,
    `similarity_score` DECIMAL(5,4) NOT NULL,
    `highlighted_segments` JSON DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_report_id` (`report_id`),
    KEY `idx_score` (`similarity_score` DESC),
    KEY `idx_submission_a` (`submission_a_id`),
    KEY `idx_submission_b` (`submission_b_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`highlighted_segments` 结构示例：
```json
[
  {
    "textA": "Spring Boot 是一个基于 Java 的框架...",
    "textB": "Spring Boot 是基于 Java 语言的框架...",
    "score": 0.92
  }
]
```

---

### 4.17 通知表 `notification`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `user_id` | BIGINT | NOT NULL | 接收通知的用户 |
| `course_id` | BIGINT | DEFAULT NULL | 关联课程 |
| `type` | ENUM(...) | NOT NULL | 通知类型（见枚举定义） |
| `title` | VARCHAR(200) | NOT NULL | 通知标题 |
| `content` | TEXT | DEFAULT NULL | 通知正文 |
| `related_id` | BIGINT | DEFAULT NULL | 关联实体 ID（如 assignment_id） |
| `is_read` | TINYINT(1) | NOT NULL DEFAULT 0 | 已读状态 |
| `deleted` | TINYINT(1) | NOT NULL DEFAULT 0 | 软删除（用户手动删除通知） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_user_id`, `idx_user_read` (user_id + is_read，用于分页查未读)

```sql
CREATE TABLE `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `course_id` BIGINT DEFAULT NULL,
    `type` ENUM(
        'ASSIGNMENT_PUBLISHED','ASSIGNMENT_URGED','ASSIGNMENT_GRADED',
        'ASSIGNMENT_RETURNED','TOPIC_REPLY','COURSE_JOINED','COURSE_ANNOUNCEMENT'
    ) NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `content` TEXT DEFAULT NULL,
    `related_id` BIGINT DEFAULT NULL,
    `is_read` TINYINT(1) NOT NULL DEFAULT 0,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.18 知识库文档块表 `knowledge_chunk`

用于 AI 答疑机器人（RAG 架构），存储课程资料切分后的向量化文本块。

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `course_id` | BIGINT | NOT NULL | 所属课程 |
| `source_type` | ENUM('MATERIAL','ASSIGNMENT','TOPIC') | NOT NULL | 来源类型 |
| `source_id` | BIGINT | NOT NULL | 来源实体 ID |
| `source_name` | VARCHAR(200) | DEFAULT NULL | 来源名称（便于展示引用） |
| `chunk_index` | INT | NOT NULL | 块序号 |
| `content` | TEXT | NOT NULL | 文本内容 |
| `qdrant_point_id` | VARCHAR(100) | DEFAULT NULL | Qdrant 中对应的 point UUID |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_course_id`, `idx_source`

```sql
CREATE TABLE `knowledge_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL,
    `source_type` ENUM('MATERIAL','ASSIGNMENT','TOPIC') NOT NULL,
    `source_id` BIGINT NOT NULL,
    `source_name` VARCHAR(200) DEFAULT NULL,
    `chunk_index` INT NOT NULL,
    `content` TEXT NOT NULL,
    `qdrant_point_id` VARCHAR(100) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 4.19 AI 答疑对话表 `chat_message`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `user_id` | BIGINT | NOT NULL | 提问学生 |
| `course_id` | BIGINT | NOT NULL | 所在课程 |
| `session_id` | VARCHAR(36) | DEFAULT NULL | 会话标识（UUID），用于区分同一课程内的多个独立对话；NULL 时按 user_id+course_id 聚合 |
| `role` | ENUM('USER','ASSISTANT') | NOT NULL | 消息角色 |
| `content` | TEXT | NOT NULL | 消息内容 |
| `references_json` | JSON | DEFAULT NULL | 引用来源（仅 ASSISTANT） |
| `create_time` | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

**索引：** `idx_user_course` (user_id + course_id，查询全局对话历史), `idx_session` (session_id，按会话翻页)

```sql
CREATE TABLE `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `course_id` BIGINT NOT NULL,
    `session_id` VARCHAR(36) DEFAULT NULL,
    `role` ENUM('USER','ASSISTANT') NOT NULL,
    `content` TEXT NOT NULL,
    `references_json` JSON DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_course` (`user_id`, `course_id`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`references_json` 结构示例：
```json
[
  {
    "chunk_id": 42,
    "source_name": "第三章课件 - Spring Boot 核心原理.pptx",
    "excerpt": "Spring Boot 自动配置通过 @EnableAutoConfiguration 注解实现..."
  }
]
```

---

## 五、索引汇总

| 表 | 索引名 | 类型 | 字段 |
|------|------|------|------|
| user | uk_username | UNIQUE | username |
| user | uk_email | UNIQUE | email |
| course | uk_course_code | UNIQUE | course_code |
| course | idx_creator_id | INDEX | creator_id |
| course | idx_status | INDEX | status |
| course_member | uk_course_user | UNIQUE | course_id, user_id |
| course_member | idx_user_id | INDEX | user_id |
| assignment | idx_course_id | INDEX | course_id |
| assignment | idx_status | INDEX | status |
| assignment | idx_course_deadline | INDEX | course_id, deadline |
| assignment_attachment | idx_assignment_id | INDEX | assignment_id |
| submission | uk_assignment_student | UNIQUE | assignment_id, student_id |
| submission | idx_student_id | INDEX | student_id |
| submission_file | idx_submission_id | INDEX | submission_id |
| material_folder | idx_course_id | INDEX | course_id |
| material_folder | idx_parent_id | INDEX | parent_id |
| material | idx_course_id | INDEX | course_id |
| material | idx_folder_id | INDEX | folder_id |
| topic | idx_course_id | INDEX | course_id |
| topic | idx_author_id | INDEX | author_id |
| topic_reply | idx_topic_id | INDEX | topic_id |
| topic_reply | idx_parent_id | INDEX | parent_id |
| topic_reply | idx_path | INDEX | path |
| lesson_draft | idx_user_id | INDEX | user_id |
| lesson_draft | idx_type | INDEX | type |
| ai_grading_config | uk_assignment_id | UNIQUE | assignment_id |
| ai_grading_result | uk_submission_id | UNIQUE | submission_id |
| similarity_report | idx_assignment_id | INDEX | assignment_id |
| similarity_pair | idx_report_id | INDEX | report_id |
| similarity_pair | idx_score | INDEX | similarity_score DESC |
| similarity_pair | idx_submission_a | INDEX | submission_a_id |
| similarity_pair | idx_submission_b | INDEX | submission_b_id |
| notification | idx_user_id | INDEX | user_id |
| notification | idx_user_read | INDEX | user_id, is_read |
| knowledge_chunk | idx_course_id | INDEX | course_id |
| knowledge_chunk | idx_source | INDEX | source_type, source_id |
| chat_message | idx_user_course | INDEX | user_id, course_id |
| chat_message | idx_session | INDEX | session_id |

---

## 六、表关系总览

| 主表 | 从表 | 关系 | 外键字段 |
|------|------|------|----------|
| user | course_member | 1:N | user_id |
| user | submission | 1:N | student_id |
| user | topic | 1:N | author_id |
| user | topic_reply | 1:N | author_id |
| user | lesson_draft | 1:N | user_id |
| user | notification | 1:N | user_id |
| user | chat_message | 1:N | user_id |
| course | course_member | 1:N | course_id |
| course | assignment | 1:N | course_id |
| course | material_folder | 1:N | course_id |
| course | material | 1:N | course_id |
| course | topic | 1:N | course_id |
| course | knowledge_chunk | 1:N | course_id |
| course | chat_message | 1:N | course_id |
| course (creator_id) | user | N:1 | creator_id → user.id |
| assignment | assignment_attachment | 1:N | assignment_id |
| assignment | submission | 1:N | assignment_id |
| assignment | ai_grading_config | 1:1 | assignment_id |
| assignment | similarity_report | 1:N | assignment_id |
| submission | submission_file | 1:N | submission_id |
| submission | ai_grading_result | 1:1 | submission_id |
| material_folder | material_folder | 1:N (自引用) | parent_id |
| material_folder | material | 1:N | folder_id |
| topic | topic_reply | 1:N | topic_id |
| topic_reply | topic_reply | 1:N (自引用) | parent_id |
| similarity_report | similarity_pair | 1:N | report_id |

---

## 七、与现有 init.sql 的对照变更

| 现有表 | 变更内容 |
|--------|----------|
| `user` | 新增 `real_name`, `avatar_url`, `enabled`, `deleted`；移除 `nickname`；`avatar` → `avatar_url` |
| `course` | 新增 `cover_url`, `creator_id`, `deleted`；`course_num` → `course_code`, `course_name` → `name`；status 改为 ENUM |
| `course_user` | 表名 → `course_member`；`create_time` → `joined_at`；新增 `deleted`（退课软删除，与 `is_archived` 归档分离） |
| `assignment` | 拆出 `assignment_attachment`（原 attachments JSON）；拆出 `ai_grading_config` / `ai_grading_result`（原 ai_grading_* 内联字段）；新增 `allow_resubmit`；新增 `idx_course_deadline` |
| `submission` | 拆出 `submission_file`（原 files JSON）；拆出 `ai_grading_result`（原 ai_score/ai_comment）；新增 `submitted_at`；`status` 改为 ENUM 含 DRAFT/RETURNED |
| `resource` | 拆分为 `material_folder` + `material` 两张表，类型枚举分离 |
| `topic` | `is_pinned` + discussion 合并为 `status` 枚举 (NORMAL/PINNED/LOCKED)；新增 `deleted`；`user_id` → `author_id` |
| `topic_reply` | 新增 `deleted`, `path`（物化路径）；`user_id` → `author_id`；新增 `idx_path` 索引 |
| `preparation` | 表名 → `lesson_draft`；`content` → `content_json`；新增 `deleted` |
| `notification` | 新增 `COURSE_ANNOUNCEMENT`, `ASSIGNMENT_RETURNED` 类型；新增 `related_id`, `deleted` |
| — (新增) | `assignment_attachment`, `submission_file`, `material_folder`, `ai_grading_config`, `ai_grading_result`, `similarity_report`, `similarity_pair`, `knowledge_chunk`, `chat_message` |

---

## 八、后续步骤

1. ✅ 数据库设计文档（本文档）
2. ⏳ 更新 `init.sql` 以匹配新设计
3. ⏳ 创建 JPA Entity 类
4. ⏳ 创建 Repository 接口
5. ⏳ API 接口设计文档
