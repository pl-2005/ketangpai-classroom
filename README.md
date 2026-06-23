# 课堂派 - 智能课堂管理系统

> 软件工程与计算II 课程设计项目  
> 技术栈：Spring Boot 4.0 + React 19 + Spring AI 2.0 + MySQL + MinIO + Redis + Qdrant

## 功能模块

### 基础功能
- 教师/学生注册登录（JWT 鉴权）
- 课程管理（创建、加入、编辑、归档、拖拽排序）
- 作业发布与提交（多文件上传、版本管理）
- 在线批阅与评分

### 附加功能
- 课程资料管理（文件夹层级、外链资源）
- 话题讨论（匿名回复、置顶、禁言）
- 备课区（教师草稿箱、一键导入）

### AI 创新功能
- **AI 智能批阅助手**：教师设定评分标准 → AI 自动预评分 + 评语
- **AI 课程答疑机器人**：RAG 架构，基于课程资料的智能问答
- **AI 作业相似度分析**：语义级 Embedding 查重

## 快速开始

### 前置要求

- JDK 21
- Node.js 22+
- Docker & Docker Compose
- Maven 3.9+

### 1. 启动基础设施

```bash
docker compose up -d
```

这将启动 MySQL (3306)、Redis (6379)、MinIO (9000/9001)。

### 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端运行在 http://localhost:8080

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:3000

### 4. 配置环境变量

```bash
# 1. 从示例文件创建 .env
cd backend
cp .env.example .env

# 2. 编辑 .env，填入实际值
#    - OPENAI_API_KEY:  必填，OpenAI API 密钥
#    - QDRANT_HOST:     必填，Qdrant Cloud 集群地址
#    - QDRANT_API_KEY:  必填，Qdrant Cloud API 密钥
#    - DB_PASSWORD:     数据库密码（默认 123456）
#    - MINIO_SECRET_KEY: MinIO 密钥（默认 minioadmin123）
#    - JWT_SECRET:      必填，JWT 签名密钥（至少 32 字节）
#    - BCRYPT_STRENGTH: BCrypt 工作因子（默认 12）
#    - REDIS_HOST:      Redis 地址，用于登录失败限流（默认 127.0.0.1）
```

> `.env` 已被 `.gitignore` 忽略，不会提交到仓库。后端启动时会自动加载 `backend/.env`；`.env.example` 仅作为模板，必须替换其中的 JWT 示例密钥。

## 项目结构

```
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/com/ketangpai/
│   │   ├── config/              # 配置类（Security, MinIO）
│   │   ├── common/              # 通用响应封装
│   │   ├── controller/          # REST 控制器
│   │   ├── dto/                 # 数据传输对象
│   │   ├── exception/           # 全局异常处理
│   │   ├── model/
│   │   │   ├── entity/          # JPA 实体
│   │   │   └── enums/           # 枚举类
│   │   ├── repository/          # JPA Repository
│   │   ├── service/             # 业务逻辑
│   │   └── ai/                  # AI 模块
│   │       ├── grading/         # 智能批阅
│   │       ├── chatbot/         # 答疑机器人
│   │       └── similarity/      # 相似度分析
│   └── src/main/resources/
│       ├── application.yml      # 应用配置
│       └── db/init.sql          # 数据库初始化
├── frontend/                    # React 前端
│   └── src/
│       ├── api/                 # API 请求层
│       ├── components/          # 通用组件
│       ├── pages/               # 页面组件
│       ├── hooks/               # 自定义 Hooks
│       ├── store/               # Zustand 状态管理
│       └── types/               # TypeScript 类型
└── docker-compose.yml           # 基础设施编排
```
