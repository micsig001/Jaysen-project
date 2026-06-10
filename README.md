# 企业级任务协同管理系统

深度集成企业微信的任务协同管理平台，支持任务数字化流转、工时统计、超时预警及协作关系可视化。

## 技术栈

- **前端**: Vue 3 + TypeScript + Element Plus + ECharts + Pinia
- **后端**: Spring Boot 3 + MyBatis-Plus + Spring Security (JWT) + Redis
- **数据库**: MySQL 8.0
- **部署**: Docker Compose

## 快速开始

### 前置要求

- Docker & Docker Compose
- Node.js 20+ (仅本地开发)
- JDK 17+ (仅本地开发)
- Maven 3.9+ (仅本地开发)

### 方式一：Docker Compose 启动（推荐）

```bash
# 复制环境变量模板
cp .env.example .env

# 修改 .env 中的配置（至少修改 DB_PASSWORD 和 JWT_SECRET）

# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

访问地址：
- 前端: http://localhost:5173
- 后端 API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### 方式二：本地开发启动

#### 后端

```bash
cd backend

# 安装依赖
mvn install

# 启动服务
mvn spring-boot:run
```

#### 前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

## 项目结构

```
task-system/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/task/
│   │   ├── auth/           # 认证模块（OAuth2.0、JWT）
│   │   ├── user/           # 用户管理
│   │   ├── task/           # 任务核心业务
│   │   ├── permission/     # 权限控制
│   │   ├── audit/          # 审计日志
│   │   ├── sync/           # 企微同步
│   │   ├── archive/        # 历史归档
│   │   ├── visualization/  # 关系图谱
│   │   ├── storage/        # 对象存储抽象
│   │   ├── idempotency/    # 幂等性控制
│   │   └── notification/   # 企微消息推送
│   ├── src/main/resources/
│   │   └── db/migration/   # Flyway 迁移脚本
│   └── pom.xml
│
├── frontend/               # Vue 3 前端
│   ├── src/
│   │   ├── views/          # 页面组件
│   │   ├── components/     # 通用组件
│   │   ├── stores/         # Pinia 状态管理
│   │   ├── api/            # API 封装
│   │   ├── router/         # 路由配置
│   │   └── utils/          # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
├── docker-compose.yml      # Docker 编排
├── .env.example            # 环境变量模板
└── README.md
```

## 核心功能

### 1. 双重确认状态机

```
CREATED → PENDING_ACCEPT → IN_PROGRESS → PENDING_VERIFY → COMPLETED
                                    ↘ REJECTED (退回 IN_PROGRESS)
               PENDING_ACCEPT → WITHDRAWN (发起人撤回)
```

- 接收方点击"确认接收"后才记录开始时间并推算截止时间
- 执行方提交后进入待验收状态，不可再编辑
- 发起方确认完成后任务完结

### 2. 企业微信集成

- OAuth2.0 免登认证
- 成员/部门增量同步（基于时间戳 Upsert）
- 应用消息推送（任务分派、驳回、超时预警）

### 3. 三级权限体系

| 角色 | 可见范围 |
|------|---------|
| EMPLOYEE | 仅自己的任务 |
| MANAGER | 本部门所有成员任务 |
| ADMIN | 全公司所有任务 |

### 4. 敏感数据脱敏

- 身份证号、银行卡号、薪资等字段默认脱敏展示
- 配置化开关控制全局启用/禁用
- ADMIN 和用户本人可查看明文

### 5. 历史数据归档

- 双重条件判断：创建时间 > 1年 AND 状态为终态
- 分批迁移（每批1000条），避免锁表
- 独立归档表存储，主表物理删除

## 环境变量配置

见 `.env.example` 文件，关键配置项：

```bash
# 数据库
DB_PASSWORD=your-password
DB_NAME=task_system

# JWT
JWT_SECRET=change-me-in-production-min-32-chars

# 企业微信
WEWORK_CORP_ID=your-corp-id
WEWORK_AGENT_ID=your-agent-id
WEWORK_SECRET=your-secret
WEWORK_MESSAGE_ENABLED=true

# 敏感数据脱敏
SENSITIVE_DATA_ENABLED=true
```

## API 文档

启动后端后访问: http://localhost:8080/swagger-ui.html

## 注意事项

1. **严禁硬编码**: 所有敏感信息通过环境变量配置
2. **事务一致性**: 核心写操作在数据库事务中完成
3. **幂等性**: 核心接口使用 UUID + Redis 去重
4. **移动端适配**: 关系图谱仅在 PC 端提供

## License

MIT
