# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

课堂派 — Web 端智能课堂管理系统（Spring Boot 4.0 + React 19 + Spring AI 2.0）。支持教师/学生两套角色，覆盖课程管理、作业批阅、资料管理、话题讨论、备课区，以及 AI 智能批阅、RAG 答疑问答、作业相似度分析。

## 常用命令

### 基础设施

```bash
docker compose up -d   # 启动 MySQL(3307), Redis(6379), MinIO(9000/9001), Qdrant(6333/6334)
docker compose down    # 停止基础设施
```

### 后端（`backend/`）

```bash
cd backend
./mvnw spring-boot:run          # 启动后端 (localhost:8080)
./mvnw test                     # 运行全部测试
./mvnw test -Dtest=ClassName    # 运行单个测试类
./mvnw compile                  # 编译
```

后端在 dev profile 下运行，自动从 `backend/.env` 加载环境变量。需先 `cp backend/.env.example backend/.env` 并填入 OpenAI/Qdrant/JWT 密钥。

### 前端（`frontend/`）

```bash
cd frontend
npm install              # 安装依赖
npm run dev              # 启动开发服务器 (localhost:3000)，/api 代理到 localhost:8080
npm run build            # 生产构建（tsc + vite build）
npm run lint             # ESLint 检查
```

## 后端架构

### 分层结构

- `controller/` — REST 控制器，调用 Service
- `service/` — 业务逻辑，**直接实现（非接口模式）**，`service/impl/` 为空勿用
- `repository/` — Spring Data JPA 接口
- `model/entity/` — JPA 实体，继承 `BaseEntity`（id, createTime, updateTime）
- `model/enums/` — 枚举类
- `dto/` — 请求/响应 DTO，按领域分子包
- `config/` — Security、MinIO、AI、Async、Scheduling 配置
- `security/` — JWT 过滤器 + `@CurrentUserId` 注解解析器
- `exception/` — `BusinessException(code, message)` + `GlobalExceptionHandler`

### 关键模式

**统一响应：** `Result<T>` 是所有 API 的返回类型。`Result.ok(data)` 返回 200，`Result.error(code, msg)` 返回错误。`GlobalExceptionHandler` 将异常自动转为 Result。

**权限控制（BaseService）：** 所有涉及课程操作的 Service 继承 `BaseService`，获得三个校验方法：
- `getMemberOrThrow(courseId, userId)` → 403 如果不是课程成员
- `getTeacherOrThrow(courseId, userId)` → 403 如果不是教师
- `getCreatorOrThrow(courseId, userId)` → 403 如果不是创建者

这也意味着 `BaseService` 子类的 `super()` 调用必须传入 `CourseMemberRepository`。

**认证：** 无状态 JWT。`SecurityConfig` 放行 `/api/auth/register`、`/api/auth/login`、`/api/health`，其余需认证。`JwtAuthenticationFilter` 从 `Authorization: Bearer <token>` 提取 token，查库获取 User，设置 SecurityContext（principal = userId Long 值）。Controller 参数可用 `@CurrentUserId Long userId` 直接获取当前用户 ID。

**文件存储：** MinIO + `FileService`。文件上传后记录到 `TempFile`（临时）或通过 `FileCleanupScheduler` 清理未引用的临时文件。

**AI 模块（`ai/` 包）：**
- `grading/` — AI 智能批阅：教师设定评分标准（Rubric），AI 预评分 + 评语。支持批量批阅任务（异步事件驱动）。
- `chatbot/` — RAG 答疑：基于课程资料的向量检索（Qdrant）增强 LLM 问答。
- `similarity/` — Embedding 级语义相似度分析。

### 数据库

Hibernate `ddl-auto: none`，表结构由 `src/main/resources/db/init.sql` 管理。

## 前端架构

### 路由（React Router v7）

全部路由见 `App.tsx`。关键约定：
- `/courses` — 课程列表
- `/courses/:courseId` — 课程详情（含作业/资料/话题 tab）
- `/courses/:courseId/assignments/:assignmentId` — 作业详情
- `/courses/:courseId/assignments/:assignmentId/grade/:submissionId` — 批阅页
- `/courses/:courseId/topics/:topicId` — 话题详情
- `/courses/:courseId/ai-chat` — AI 答疑
- `/courses/:courseId/assignments/:assignmentId/similarity` — 相似度分析

嵌套路由结构：`AppLayout`（ProtectedRoute 包裹）作为父路由，菜单 + `<Outlet />` 布局。

### 状态管理

- `AuthContext` (`contexts/AuthContext.tsx`) — 提供 `user`、`token`、`login()`、`logout()`、`refreshUser()`。token 存 localStorage，启动时验证有效性。
- `store/` 目录用于 Zustand store（当前空）。

### API 层（`api/`）

每个领域一个目录，包含 API 文件 + 类型定义。所有请求通过 `utils/request.ts` 的 Axios 实例，自动注入 JWT。响应拦截器：`code=200` 返回 `data.data`；`code=401` 跳转登录页。SSE 流式请求使用 `fetchSSE()`（基于 fetch + ReadableStream，用于 AI 聊天流式输出）。

### 组件约定

- 页面组件在 `pages/`，按模块分子目录
- 通用组件在 `components/`
- Ant Design 5.x + `@ant-design/icons`
- Vite alias `@` → `/src`
