# 课堂派 — API 接口设计文档

> 版本：v1.0  
> 日期：2026-06-23  
> 规范：RESTful，JSON 请求/响应，JWT Bearer Token 鉴权

---

## 一、通用约定

### 1.1 基础路径

```
生产环境：https://api.ketangpai.example.com
开发环境：http://localhost:8080
```

### 1.2 统一响应格式

所有接口返回 `Result<T>` 结构：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 业务冲突（如已加入课程、重复提交） |
| 429 | 请求过于频繁（如登录失败次数过多） |
| 500 | 服务器内部错误 |

### 1.3 认证方式

```
Authorization: Bearer <JWT_TOKEN>
```

除 `/api/auth/**` 外，所有接口需要携带 Token。

### 1.4 分页参数

```
GET /api/xxx?page=0&size=20&sort=createTime,desc
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 0 | 页码（从 0 开始） |
| `size` | int | 20 | 每页条数 |
| `sort` | string | createTime,desc | 排序字段与方向 |

分页响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [],
    "totalElements": 100,
    "totalPages": 5,
    "number": 0,
    "size": 20
  }
}
```

---

## 二、API 索引

| 模块 | 接口数 | 路径前缀 |
|------|--------|----------|
| 账号认证 | 3 | `/api/auth` |
| 用户管理 | 3 | `/api/user` |
| 课程管理 | 11 | `/api/courses` |
| 作业管理 | 6 | `/api/assignments` |
| 提交管理 | 5 | `/api/submissions` |
| 资料管理 | 6 | `/api/materials` |
| 话题讨论 | 7 | `/api/topics` |
| 备课区 | 4 | `/api/drafts` |
| AI 批阅 | 4 | `/api/ai-grading` |
| 相似度分析 | 3 | `/api/similarity` |
| AI 答疑 | 4 | `/api/ai-chat` |
| 通知管理 | 4 | `/api/notifications` |
| 文件上传 | 2 | `/api/files` |

---

## 三、接口详细设计

### 3.1 账号认证

#### 3.1.1 注册

```
POST /api/auth/register
```

**请求体：**

```json
{
  "username": "zhangsan",
  "password": "Abc123456!",
  "email": "zhangsan@example.com",
  "realName": "张三",
  "role": "STUDENT"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 是 | 4–50 字符，字母开头 |
| `password` | string | 是 | 8–32 字符，含大小写+数字 |
| `email` | string | 否 | 邮箱格式 |
| `realName` | string | 否 | 真实姓名 |
| `role` | enum | 是 | `TEACHER` / `STUDENT` |

**成功响应 (201)：**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "role": "STUDENT"
  }
}
```

**错误场景：**

| 场景 | code |
|------|------|
| 用户名已存在 | 409 |
| 参数校验失败 | 400 |

---

#### 3.1.2 登录

```
POST /api/auth/login
```

**请求体：**

```json
{
  "username": "zhangsan",
  "password": "Abc123456!"
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "zhangsan",
      "realName": "张三",
      "role": "STUDENT",
      "avatarUrl": null
    }
  }
}
```

---

#### 3.1.3 获取当前用户信息

```
GET /api/auth/me
```

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "realName": "张三",
    "email": "zhangsan@example.com",
    "role": "STUDENT",
    "avatarUrl": "http://minio:9000/ketangpai/avatars/1.png"
  }
}
```

---

### 3.2 用户管理

#### 3.2.1 更新个人信息

```
PUT /api/user/profile
```

**请求体：**

```json
{
  "realName": "张三",
  "email": "zhangsan@example.com"
}
```

---

#### 3.2.2 修改密码

```
PUT /api/user/password
```

```json
{
  "oldPassword": "Abc123456!",
  "newPassword": "NewPass789!"
}
```

---

#### 3.2.3 上传头像

```
POST /api/user/avatar
Content-Type: multipart/form-data

file: <binary>
```

**响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "avatarUrl": "http://minio:9000/ketangpai/avatars/1.png"
  }
}
```

---

### 3.3 课程管理

#### 3.3.1 获取我的课程列表

```
GET /api/courses
```

**查询参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `archived` | boolean | false | true=查看归档列表 |
| `page` | int | 0 | |
| `size` | int | 12 | |

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "软件工程与计算II",
        "courseCode": "SE2026",
        "coverUrl": "...",
        "status": "ACTIVE",
        "memberCount": 35,
        "role": "CREATOR",
        "isArchived": false,
        "sortOrder": 0,
        "createTime": "2026-06-01T10:00:00"
      }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 12
  }
}
```

---

#### 3.3.2 创建课程

```
POST /api/courses
```

**请求体：**

```json
{
  "name": "软件工程与计算II",
  "description": "2026年秋季学期",
  "coverUrl": "http://minio:9000/ketangpai/covers/1.png"
}
```

**响应 (201)：** 返回完整的课程对象（含自动生成的 `courseCode`）。

---

#### 3.3.3 通过课程号加入课程

```
POST /api/courses/join
```

```json
{
  "courseCode": "SE2026"
}
```

**错误场景：** 课程不存在 (404)，已加入 (409)，课程已归档 (400)。

---

#### 3.3.4 获取课程详情

```
GET /api/courses/{courseId}
```

**响应包含：** 课程基本信息 + 当前用户在课程中的角色 + 成员数量。

---

#### 3.3.5 获取课程成员列表

```
GET /api/courses/{courseId}/members
```

**查询参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `role` | string | — | 筛选角色：`TEACHER` / `STUDENT` |
| `page` | int | 0 | |
| `size` | int | 30 | |

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "userId": 2,
        "username": "lisi",
        "realName": "李四",
                "avatarUrl": null,
                "accountRole": "STUDENT",
                "role": "STUDENT",
        "joinedAt": "2026-06-05T14:00:00"
      }
    ]
  }
}
```

---

#### 3.3.6 更新课程信息

```
PUT /api/courses/{courseId}
```

权限：仅 CREATOR / TEACHER。

```json
{
  "name": "软件工程与计算II（更新）",
  "description": "更新后的课程简介",
  "coverUrl": "..."
}
```

---

#### 3.3.7 课程操作（归档 / 恢复 / 退课 / 删除）

```
POST /api/courses/{courseId}/action
```

```json
{
  "action": "ARCHIVE"
}
```

| action | 角色权限 | 说明 |
|--------|----------|------|
| `ARCHIVE` | 所有人 | 仅归档自己（`isArchived = true`） |
| `UNARCHIVE` | 所有人 | 恢复归档 |
| `ARCHIVE_FOR_ALL` | CREATOR | 归档课程本身 + 所有人 |
| `RESTORE_FOR_ALL` | CREATOR | 恢复课程本身，重新进入所有成员的正常/个人归档视图 |
| `LEAVE` | STUDENT / TEACHER | 退课（`deleted = true`） |
| `DELETE` | CREATOR | 将课程移入创建者回收站（`course.deleted = true`） |

#### 3.3.8 设置课程共管角色

```
PUT /api/courses/{courseId}/members/{memberUserId}/role
```

权限：仅 `CREATOR`。目标用户必须已经加入课程；只有全局身份为教师的账号可以设置为 `TEACHER`。

```json
{
  "role": "TEACHER"
}
```

将 `role` 设置为 `STUDENT` 可撤销共管权限，不能修改课程创建者的角色。

#### 3.3.9 更新课程卡片顺序

```
PUT /api/courses/order
```

```json
{
  "items": [
    {
      "courseId": 3,
      "sortOrder": 0
    },
    {
      "courseId": 1,
      "sortOrder": 1
    }
  ]
}
```

仅可调整当前用户已加入且未退课的课程。`courseId` 和 `sortOrder` 在单次请求中均不可重复。

#### 3.3.10 获取课程回收站

```
GET /api/courses/trash?page=0&size=12
```

仅返回当前用户作为 `CREATOR` 删除的课程，包含删除时间 `deletedAt`。

#### 3.3.11 回收站操作

```
POST /api/courses/{courseId}/trash/action
```

恢复课程：

```json
{
  "action": "RESTORE"
}
```

永久删除：

```json
{
  "action": "PURGE"
}
```

`PURGE` 会在同一事务中物理删除课程及其成员、作业、提交、资料、话题、通知和 AI 关联数据，操作不可撤销。

---

### 3.4 作业管理

#### 3.4.1 获取课程作业列表

```
GET /api/courses/{courseId}/assignments
```

**查询参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `status` | string | — | `DRAFT` / `PUBLISHED` / `CLOSED` |
| `page` | int | 0 | |
| `size` | int | 20 | |

**教师视角：** 返回所有状态的作业。  
**学生视角：** 仅返回 `PUBLISHED` / `CLOSED` 作业，附加本人的提交状态 `mySubmissionStatus`；请求 `DRAFT` 返回 403。

列表使用分页 DTO 返回，字段包括 `id`、`courseId`、`title`、`status`、`deadline`、`maxScore`、`allowResubmit`、`mySubmissionStatus` 和 `createTime`，不直接暴露 JPA 实体。

---

#### 3.4.2 获取作业详情

```
GET /api/assignments/{assignmentId}
```

**响应（教师视角）：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "courseId": 1,
    "title": "第一次作业：需求分析",
    "content": "<p>请完成以下需求分析题目...</p>",
    "status": "PUBLISHED",
    "deadline": "2026-06-30T23:59:59",
    "maxScore": 100,
    "allowResubmit": true,
    "attachments": [
      {
        "id": 1,
        "fileName": "需求分析模板.docx",
        "fileUrl": "...",
        "fileSize": 102400
      }
    ],
    "aiGradingConfig": {
      "enabled": true,
      "gradingStyle": "BALANCED"
    },
    "stats": {
      "totalStudents": 30,
      "submittedCount": 22,
      "gradedCount": 10
    },
    "createTime": "2026-06-10T09:00:00"
  }
}
```

**响应（学生视角）：** 不含 `stats` 和 `aiGradingConfig`，含本人的提交状态 `mySubmissionStatus`。

---

#### 3.4.3 创建作业

```
POST /api/assignments
```

权限：教师。

```json
{
  "courseId": 1,
  "title": "第一次作业：需求分析",
  "content": "<p>作业描述（富文本 HTML）</p>",
  "deadline": "2026-06-30T23:59:59",
  "maxScore": 100,
  "allowResubmit": true,
  "attachmentIds": [1, 2, 3]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `courseId` | long | 是 | |
| `title` | string | 是 | 最长 200 字符 |
| `content` | string | 否 | 富文本 HTML |
| `deadline` | datetime | 否 | |
| `maxScore` | int | 否 | 默认 100 |
| `allowResubmit` | boolean | 否 | 默认 true |
| `attachmentIds` | long[] | 否 | 已上传的附件 ID 列表 |

---

#### 3.4.4 更新作业

```
PUT /api/assignments/{assignmentId}
```

权限：教师。仅 `DRAFT` 状态可大幅修改；`PUBLISHED` 状态仅可修改 `deadline` 和 `content`。

---

#### 3.4.5 发布 / 关闭作业

```
POST /api/assignments/{assignmentId}/status
```

```json
{
  "status": "PUBLISHED"
}
```

| 操作 | 说明 |
|------|------|
| `DRAFT → PUBLISHED` | 发布作业，所有学生收到通知 |
| `PUBLISHED → CLOSED` | 关闭提交，学生不可再提交 |

---

#### 3.4.6 催交

```
POST /api/assignments/{assignmentId}/urge
```

权限：教师。所有未提交学生收到催交通知。

```json
{
  "studentIds": [2, 3, 5]
}
```

`studentIds` 为空则催交所有未提交学生。

---

### 3.5 提交管理

#### 3.5.1 学生提交作业

```
POST /api/assignments/{assignmentId}/submit
```

权限：学生。

```json
{
  "content": "我的作业正文内容...",
  "fileIds": [1, 2]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 否 | 文本内容 |
| `fileIds` | long[] | 否 | 已上传的文件 ID |

**行为：**
- 首次提交：创建 Submission（status = SUBMITTED，version = 1）
- 重复提交（`allowResubmit = true`）：version + 1，覆盖旧文件和内容；若之前状态为 RETURNED，重新置为 SUBMITTED
- 重复提交（`allowResubmit = false`）：返回 409

---

#### 3.5.2 获取某作业的全部提交（教师）

```
GET /api/assignments/{assignmentId}/submissions
```

**查询参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `status` | string | — | `SUBMITTED` / `GRADED` / `RETURNED` |
| `page` | int | 0 | |
| `size` | int | 20 | |

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "studentId": 2,
        "studentName": "李四",
        "studentUsername": "lisi",
        "content": "...",
        "status": "SUBMITTED",
        "score": null,
        "version": 1,
        "files": [
          {
            "id": 1,
            "fileName": "需求分析报告.pdf",
            "fileUrl": "...",
            "fileSize": 204800
          }
        ],
        "aiGradingResult": null,
        "submittedAt": "2026-06-20T18:30:00",
        "gradedAt": null
      }
    ]
  }
}
```

---

#### 3.5.3 获取单个提交详情

```
GET /api/submissions/{submissionId}
```

---

#### 3.5.4 教师批阅评分

```
PUT /api/submissions/{submissionId}/grade
```

权限：教师。

```json
{
  "score": 85,
  "teacherComment": "整体完成得不错，但第三问分析不够深入。"
}
```

**效果：** status → GRADED，学生收到批阅通知。

---

#### 3.5.5 教师退回作业

```
POST /api/submissions/{submissionId}/return
```

权限：教师。

```json
{
  "reason": "请补充第二章的分析数据"
}
```

**效果：** status → RETURNED，学生收到退回通知，可修改后重新提交。

---

### 3.6 资料管理

#### 3.6.1 获取课程资料目录树

```
GET /api/courses/{courseId}/materials/tree
```

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "folders": [
      {
        "id": 1,
        "name": "课件",
        "parentId": null,
        "sortOrder": 0,
        "children": [
          {
            "id": 2,
            "name": "第一章",
            "parentId": 1,
            "sortOrder": 0,
            "children": []
          }
        ],
        "materials": [
          {
            "id": 1,
            "title": "课程大纲.pdf",
            "type": "FILE",
            "fileUrl": "...",
            "fileSize": 512000,
            "linkUrl": null,
            "sortOrder": 0
          }
        ]
      }
    ]
  }
}
```

---

#### 3.6.2 创建文件夹

```
POST /api/materials/folders
```

```json
{
  "courseId": 1,
  "parentId": null,
  "name": "课件"
}
```

---

#### 3.6.3 上传资料

```
POST /api/materials
```

```json
{
  "courseId": 1,
  "folderId": 1,
  "type": "FILE",
  "title": "第一章课件.pptx",
  "fileUrl": "http://minio:9000/ketangpai/materials/xxx.pptx",
  "fileSize": 2048000
}
```

外链资料：

```json
{
  "courseId": 1,
  "folderId": 1,
  "type": "LINK",
  "title": "参考链接：Spring Boot 官方文档",
  "linkUrl": "https://spring.io/projects/spring-boot"
}
```

---

#### 3.6.4 移动资料 / 文件夹

```
PUT /api/materials/{materialId}/move
```

```json
{
  "targetFolderId": 2
}
```

---

#### 3.6.5 更新资料信息

```
PUT /api/materials/{materialId}
```

```json
{
  "title": "推荐阅读材料",
  "sortOrder": 1
}
```

---

#### 3.6.6 删除资料 / 文件夹

```
DELETE /api/materials/{materialId}      // 资料软删除
DELETE /api/materials/folders/{folderId} // 文件夹及内容全部软删除
```

---

### 3.7 话题讨论

#### 3.7.1 获取课程话题列表

```
GET /api/courses/{courseId}/topics
```

**响应：** 置顶话题优先，然后按时间倒序。匿名话题隐藏 `authorName`。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "关于需求分析方法的讨论",
        "content": "大家在需求分析中遇到了哪些问题？",
        "status": "PINNED",
        "authorId": 1,
        "authorName": "王老师",
        "isAnonymous": false,
        "discussionEnabled": true,
        "replyCount": 12,
        "createTime": "2026-06-15T10:00:00"
      }
    ]
  }
}
```

---

#### 3.7.2 获取话题详情（含回复列表）

```
GET /api/topics/{topicId}
```

查询参数：`page`（回复分页）。

**回复按物化路径 `path` 排序**，实现楼中楼正确展示。

---

#### 3.7.3 创建话题

```
POST /api/topics
```

```json
{
  "courseId": 1,
  "title": "关于需求分析方法的讨论",
  "content": "大家在需求分析中遇到了哪些问题？",
  "isAnonymous": false
}
```

---

#### 3.7.4 回复话题

```
POST /api/topics/{topicId}/replies
```

```json
{
  "content": "我赞同，数据流图特别容易画错。",
  "isAnonymous": false,
  "parentId": 3
}
```

`parentId` 为 NULL 表示直接回复话题（楼层回复）；非 NULL 表示楼中楼回复。

---

#### 3.7.5 话题操作（置顶 / 锁定 / 开关讨论）

```
POST /api/topics/{topicId}/status
```

权限：教师。

```json
{
  "status": "LOCKED"
}
```

| status | 说明 |
|--------|------|
| `NORMAL` | 正常 |
| `PINNED` | 置顶 |
| `LOCKED` | 锁定（禁止新回复） |

---

#### 3.7.6 更新话题

```
PUT /api/topics/{topicId}
```

权限：作者本人或教师。教师可编辑任意话题，作者仅可编辑自己的话题。

---

#### 3.7.7 删除话题 / 回复

```
DELETE /api/topics/{topicId}
DELETE /api/topics/replies/{replyId}
```

权限：作者本人或教师。软删除。

---

### 3.8 备课区

#### 3.8.1 获取备课区草稿列表

```
GET /api/drafts
```

查询参数：`type=ASSIGNMENT`（可选筛选类型）。

---

#### 3.8.2 保存草稿

```
POST /api/drafts
```

```json
{
  "type": "ASSIGNMENT",
  "title": "期中作业草稿",
  "contentJson": "{\"title\":\"...\",\"content\":\"...\",\"deadline\":\"...\"}"
}
```

`contentJson` 为结构化 JSON 字符串，内容结构与对应实体创建接口一致。

---

#### 3.8.3 更新草稿

```
PUT /api/drafts/{draftId}
```

---

#### 3.8.4 发布草稿（一键导入）

```
POST /api/drafts/{draftId}/publish
```

```json
{
  "courseId": 1
}
```

**效果：** 反序列化 `contentJson`，调用对应实体的创建逻辑（创建作业 / 资料 / 话题），成功后删除草稿。

---

#### 3.8.5 删除草稿

```
DELETE /api/drafts/{draftId}
```

---

### 3.9 AI 智能批阅

#### 3.9.1 配置 AI 批阅

```
PUT /api/assignments/{assignmentId}/ai-grading-config
```

权限：教师。

```json
{
  "enabled": true,
  "promptTemplate": "请根据以下评分标准批改作业，给出分数和详细评语...",
  "rubric": [
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
  ],
  "gradingStyle": "BALANCED"
}
```

---

#### 3.9.2 获取 AI 批阅配置

```
GET /api/assignments/{assignmentId}/ai-grading-config
```

---

#### 3.9.3 触发 AI 批阅（对单个提交）

```
POST /api/submissions/{submissionId}/ai-grade
```

**触发方式：**
- **自动**：学生首次提交后异步触发
- **手动**：教师在批阅页面点击"AI 预评分"

**响应（轮询 / 长轮询）：**

```json
{
  "code": 200,
  "message": "AI 批阅完成",
  "data": {
    "score": 82,
    "comment": "整体作业完成度较高...",
    "suggestions": "建议在第三部分补充更多数据分析...",
    "detail": [
      {
        "dimension": "内容完整性",
        "score": 24,
        "maxScore": 30,
        "comment": "要点基本覆盖，但问题三回答不够完整"
      },
      {
        "dimension": "逻辑结构",
        "score": 20,
        "maxScore": 25,
        "comment": "结构清晰，但章节之间过渡可以更自然"
      }
    ],
    "gradedAt": "2026-06-20T18:32:00"
  }
}
```

---

#### 3.9.4 批量 AI 批阅（某作业全部未批提交）

```
POST /api/assignments/{assignmentId}/ai-grade-batch
```

权限：教师。异步执行，返回任务 ID，前端轮询进度。

```json
{
  "code": 200,
  "message": "批量 AI 批阅已启动",
  "data": {
    "taskId": "batch-123-abc",
    "totalCount": 22
  }
}
```

---

### 3.10 作业相似度分析

#### 3.10.1 触发相似度分析

```
POST /api/assignments/{assignmentId}/similarity/analyze
```

权限：教师。

```json
{
  "threshold": 0.80
}
```

**响应：**

```json
{
  "code": 200,
  "message": "相似度分析完成",
  "data": {
    "reportId": 1,
    "totalSubmissions": 22,
    "threshold": 0.80,
    "suspiciousCount": 3,
    "generatedAt": "2026-06-21T10:00:00"
  }
}
```

**处理流程（异步）：**
1. 获取该作业所有已提交的提交
2. 调用 Embedding API 向量化每份提交
3. 计算两两余弦相似度矩阵
4. 超阈值对持久化到 `similarity_pair`
5. 生成报告存到 `similarity_report`

---

#### 3.10.2 获取相似度报告列表

```
GET /api/assignments/{assignmentId}/similarity/reports
```

---

#### 3.10.3 获取相似度报告详情

```
GET /api/similarity/reports/{reportId}
```

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "report": {
      "id": 1,
      "assignmentId": 1,
      "totalSubmissions": 22,
      "threshold": 0.80
    },
    "pairs": [
      {
        "id": 1,
        "submissionA": {
          "id": 5,
          "studentId": 2,
          "studentName": "李四"
        },
        "submissionB": {
          "id": 12,
          "studentId": 7,
          "studentName": "赵六"
        },
        "similarityScore": 0.9234,
        "highlightedSegments": [
          {
            "textA": "Spring Boot 是一个基于 Java 的框架...",
            "textB": "Spring Boot 是基于 Java 语言的框架...",
            "score": 0.92
          }
        ]
      }
    ]
  }
}
```

排序：`similarityScore` 降序。

---

### 3.11 AI 答疑机器人

#### 3.11.1 创建新会话

```
POST /api/courses/{courseId}/ai-chat/sessions
```

```json
{
  "title": "关于自动配置的问题"
}
```

**响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

---

#### 3.11.2 获取会话列表

```
GET /api/courses/{courseId}/ai-chat/sessions
```

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "sessionId": "550e8400-...",
      "title": "关于自动配置的问题",
      "lastMessage": "Spring Boot 的自动配置是通过 @EnableAutoConfiguration 实现的...",
      "lastTime": "2026-06-20T15:30:00"
    }
  ]
}
```

---

#### 3.11.3 发送提问（RAG 检索 + LLM 回答）

```
POST /api/courses/{courseId}/ai-chat
```

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Spring Boot 的自动配置是如何工作的？"
}
```

**响应（流式 SSE，可选）：**

```
Content-Type: text/event-stream

event: start
data: {"sessionId":"550e8400-..."}

event: chunk
data: {"content":"Spring Boot 的自动配置通过 "}

event: references
data: {"references":[{"chunkId":42,"sourceName":"第三章课件.pptx","excerpt":"..."}]}

event: done
data: {}
```

**非流式响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 10,
    "sessionId": "550e8400-...",
    "role": "ASSISTANT",
    "content": "Spring Boot 的自动配置是通过 @EnableAutoConfiguration 注解实现的。它会根据类路径中的依赖自动配置 Spring 应用...",
    "references": [
      {
        "chunkId": 42,
        "sourceName": "第三章课件 - Spring Boot 核心原理.pptx",
        "excerpt": "Spring Boot 自动配置通过 @EnableAutoConfiguration 注解实现自动装配..."
      }
    ],
    "createTime": "2026-06-20T15:30:01"
  }
}
```

---

#### 3.11.4 获取会话历史

```
GET /api/courses/{courseId}/ai-chat/sessions/{sessionId}
```

查询参数：`page`, `size`。

---

#### 3.11.5 删除会话

```
DELETE /api/courses/{courseId}/ai-chat/sessions/{sessionId}
```

---

### 3.12 通知管理

#### 3.12.1 获取通知列表

```
GET /api/notifications
```

查询参数：`page`, `size`, `type`（可选，筛选类型）。

---

#### 3.12.2 获取未读通知数

```
GET /api/notifications/unread-count
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "count": 5
  }
}
```

---

#### 3.12.3 标记已读

```
PUT /api/notifications/{notificationId}/read
```

```
PUT /api/notifications/read-all        // 全部已读
```

---

#### 3.12.4 删除通知

```
DELETE /api/notifications/{notificationId}
```

软删除。

---

### 3.13 文件上传

#### 3.13.1 上传文件

```
POST /api/files/upload
Content-Type: multipart/form-data

file: <binary>
```

**响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "id": 123,
    "fileName": "需求分析报告.pdf",
    "fileUrl": "http://minio:9000/ketangpai/files/2026/06/xxx.pdf",
    "fileSize": 204800
  }
}
```

**说明：** 上传操作统一走此接口，文件暂存 MinIO。后续创建作业提交、资料等时，传入返回的 `id` 关联。

**限制：**
- 单文件不超过 50MB
- 支持格式：PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX, TXT, ZIP, RAR, PNG, JPG, JPEG, GIF, MP4

---

#### 3.13.2 预览 / 下载文件

```
GET /api/files/{fileId}/download
```

生成临时签名 URL（有效期 30 分钟），302 重定向到 MinIO 直链。

```
GET /api/files/{fileId}/preview
```

对支持在线预览的格式（PDF、图片），返回内嵌预览 URL。

---

## 四、文件上传流程说明

为避免孤儿文件（上传了但未关联到任何实体），采用 **"先上传，后关联"** 模式：

```
1. 前端调用 POST /api/files/upload → 获得 fileId
2. 前端将 fileId 传给业务接口（如提交作业、创建资料）
3. 业务接口建立文件与实体的关联关系
4. 定期清理超过 24 小时未被关联的临时文件（定时任务）
```

数据库中的关联通过 `assignment_attachment.assignment_id`、`submission_file.submission_id` 等实现。

---

## 五、权限矩阵

| 接口前缀 | 未登录 | STUDENT | TEACHER | CREATOR |
|----------|--------|---------|---------|---------|
| `/api/auth/**` | ✅ | ✅ | ✅ | ✅ |
| `/api/user/**` | — | ✅（本人） | ✅（本人） | ✅（本人） |
| `/api/courses` GET | — | ✅（已加入） | ✅（已加入） | ✅（已加入） |
| `/api/courses` 创建 | — | — | ✅ | ✅ |
| `/api/courses/*` 修改/删除 | — | — | ✅（同课程） | ✅（自己的课） |
| `/api/assignments` 创建/修改 | — | — | ✅（同课程） | ✅（同课程） |
| `/api/assignments` 查看 | — | ✅（同课程） | ✅（同课程） | ✅（同课程） |
| `/api/submissions` 提交 | — | ✅（本人） | — | — |
| `/api/submissions` 批阅 | — | — | ✅（同课程） | ✅（同课程） |
| `/api/materials` 管理 | — | — | ✅（同课程） | ✅（同课程） |
| `/api/materials` 查看 | — | ✅（同课程） | ✅（同课程） | ✅（同课程） |
| `/api/topics` 管理 | — | ✅（作者） | ✅（同课程） | ✅（同课程） |
| `/api/drafts` | — | — | ✅（本人） | ✅（本人） |
| `/api/ai-grading` | — | — | ✅（同课程） | ✅（同课程） |
| `/api/ai-chat` | — | ✅（同课程） | ✅（同课程） | ✅（同课程） |
| `/api/similarity` | — | — | ✅（同课程） | ✅（同课程） |
| `/api/notifications` | — | ✅（本人） | ✅（本人） | ✅（本人） |
| `/api/files` 上传 | — | ✅ | ✅ | ✅ |
| `/api/files` 下载 | — | ✅（同课程） | ✅（同课程） | ✅（同课程） |

---

## 六、状态流转

### 6.1 作业生命周期

```
DRAFT ──发布──▶ PUBLISHED ──关闭──▶ CLOSED
                    │
                    └──（学生提交）──▶ submission.SUBMITTED
```

### 6.2 提交生命周期

```
DRAFT ──学生提交──▶ SUBMITTED ──教师批阅──▶ GRADED
                       │                        │
                       └──教师退回──▶ RETURNED ──┘（学生重新提交）
```

### 6.3 话题生命周期

```
NORMAL ◀──▶ PINNED（置顶/取消）
NORMAL ──锁定──▶ LOCKED
LOCKED ──解锁──▶ NORMAL
```

---

## 七、WebSocket / SSE 设计

| 信道 | 用途 | 说明 |
|------|------|------|
| `GET /api/ai-chat/stream?sessionId=xxx` | AI 答疑流式响应 | SSE，逐 token 推送 |
| `WS /ws/notifications` | 实时通知推送 | WebSocket，新通知到达时推送 `unreadCount` |

---

## 八、待定事项

1. 国际化错误消息（i18n）
2. API 版本控制策略（当前未加 `/v1` 前缀，后续可追加）
3. 接口限流（Rate Limiting）
4. 操作日志（Audit Log）
