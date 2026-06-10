# 企业级任务协同管理系统

> 企业微信深度集成 · 双重确认状态机 · 三级权限隔离 · 全链路可审计

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-brightgreen)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

---

## 项目简介

企业内部任务管理工具，深度集成**企业微信**，实现任务的数字化流转、工时统计、超时预警以及复杂协作关系的可视化。适用于 50-500 人规模的中小型团队。

### 核心特性

- ✅ **企业微信免登录** —— 员工在企微工作台点击应用即用，零学习成本
- ✅ **双重确认状态机** —— 任务接收和验收的严谨闭环，避免扯皮
- ✅ **三级权限隔离** —— EMPLOYEE / MANAGER / ADMIN 自动按部门/全公司隔离可见范围
- ✅ **全链路审计** —— 异步记录所有敏感操作（创建/更新/删除/状态变更），含变更前后快照
- ✅ **敏感数据脱敏** —— 身份证、银行卡、薪资等字段自动脱敏，二次确认可查看明文
- ✅ **幂等性保障** —— UUID + Redis SETNX + 数据库 UNIQUE 三重机制，重复请求自动返回首次结果
- ✅ **可视化关系图** —— 单人辐射图 + 多人全景图，ECharts Graph 力导向布局
- ✅ **归档防雷** —— 创建 > 1年 且 状态为终态才归档，长周期任务永不被误删

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | Web 框架 |
| MyBatis-Plus | 3.5.5 | ORM |
| Spring Security | 6.x | 认证授权 + JWT |
| JJWT | 0.12.3 | JWT 签发/校验 |
| Spring Data Redis | - | 缓存 + 分布式锁 + 幂等性 |
| MySQL | 8.0 | 主存储 |
| Flyway | - | 数据库迁移 |
| Lombok | - | 减少样板代码 |
| springdoc-openapi | 2.3.0 | Swagger UI |
| Java | 17 | 运行时 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4 | 主框架（Composition API） |
| TypeScript | 5.x | 类型安全 |
| Vite | 5.x | 构建工具 |
| Element Plus | 最新 | UI 组件库 |
| Pinia | - | 状态管理 |
| Vue Router | 4.x | 路由 |
| ECharts | 5.x | 可视化图表 |
| Axios | - | HTTP 客户端 |
| CryptoJS | - | Token 加密 |

### 部署

| 技术 | 用途 |
|------|------|
| Docker Compose | 多服务编排（MySQL + Redis + Backend + Frontend） |
| Nginx | 前端静态资源服务 |

---

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- Docker Desktop（推荐，可一键启动所有依赖）

### 方式 A：Docker 一键启动（推荐）

```bash
# 1. 克隆仓库
git clone https://github.com/micsig001/Jaysen-project.git
cd Jaysen-project

# 2. 准备环境变量
cp .env.example .env
# 编辑 .env，**必须**修改：
#   - JWT_SECRET（至少 32 字符，可用 openssl rand -base64 32 生成）
#   - REDIS_PASSWORD
#   - WEWORK_CORP_ID / WEWORK_AGENT_ID / WEWORK_SECRET（从企业微信后台获取）
#   - VITE_TOKEN_SECRET（前端用，至少 32 字符）

# 3. 启动所有服务
docker-compose up -d

# 4. 验证
# 前端：浏览器打开 http://localhost:5173
# 后端 API：访问 http://localhost:8080/swagger-ui.html
# 数据库：mysql -h 127.0.0.1 -u root -p task_system
```

### 方式 B：本地开发（前后端分离）

**启动后端：**

```bash
cd backend
# 修改 src/main/resources/application.yml 中的数据库/Redis 地址
mvn spring-boot:run
```

**启动前端：**

```bash
cd frontend
npm install
cp ../.env.example .env
# 修改 .env 中的 VITE_API_URL（指向后端 8080）和 VITE_TOKEN_SECRET
npm run dev
```

浏览器打开 http://localhost:5173

### 默认账号

应用启动后**没有默认账号**——必须先在企业微信后台配置应用，然后通过企微工作台点击应用登录。

第一次登录的用户自动成为 ADMIN 角色（如果数据库中没有用户记录），后续可在管理界面调整。

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                         企业微信工作台                            │
│                     (员工点击应用免登录)                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Frontend (Vue 3)                        │
│  ┌──────────────┬──────────────┬──────────────┬───────────┐  │
│  │  登录 / 注销   │   任务工作台  │   可视化图谱   │  管理界面  │  │
│  └──────────────┴──────────────┴──────────────┴───────────┘  │
│       ▲                                                      │
│       │ JWT (加密存储)                                         │
└───────┼──────────────────────────────────────────────────────┘
        │ HTTPS
        ▼
┌─────────────────────────────────────────────────────────────┐
│                  Backend (Spring Boot 3)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Filter Layer: JwtAuthenticationFilter (Spring Security) │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  AOP Layer: @Idempotent + @AuditLog + @SensitiveData  │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │   Auth   │   Task   │ Archive  │   Sync   │  Visual  │  │
│  │ Controller│ Service  │ Service  │ Service  │ Service  │  │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘  │
└───────┬─────────────────────────────────────┬───────────────┘
        │                                     │
        ▼                                     ▼
┌──────────────────┐              ┌──────────────────────────┐
│      MySQL       │              │          Redis           │
│  (业务数据)        │              │  (缓存/锁/幂等Key/黑名单)   │
└──────────────────┘              └──────────────────────────┘
```

---

## 目录结构

```
.
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/task/
│   │   ├── auth/                     # JWT 认证
│   │   ├── archive/                  # 归档模块
│   │   ├── audit/                    # 审计 AOP
│   │   ├── common/                   # 通用类（Result/BusinessException/...）
│   │   ├── config/                   # 配置（Security/Swagger/...）
│   │   ├── controller/               # REST 控制器
│   │   ├── entity/                   # 数据库实体
│   │   ├── idempotency/              # 幂等性 AOP
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   ├── privacy/                  # 敏感数据脱敏 AOP
│   │   ├── scheduler/                # 定时任务（超时检查）
│   │   ├── service/                  # 业务服务
│   │   ├── storage/                  # 对象存储（TODO）
│   │   ├── user/                     # 用户管理（TODO）
│   │   ├── util/                     # 工具类
│   │   ├── visualization/            # 可视化
│   │   └── wework/                   # 企业微信对接
│   └── src/main/resources/
│       ├── application.yml           # 全局配置
│       └── db/migration/             # Flyway V1/V2/V3 SQL
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── api/                      # API 封装
│   │   ├── components/               # 通用组件（SensitiveText）
│   │   ├── directives/               # 自定义指令（v-permission）
│   │   ├── router/                   # 路由
│   │   ├── stores/                   # Pinia 状态
│   │   ├── utils/                    # 工具（request/crypto）
│   │   └── views/                    # 页面（7 个）
│   └── package.json
├── docs/                             # 文档（待补充）
├── docker-compose.yml                # Docker 编排
├── .env.example                      # 环境变量模板
├── .gitignore                        # Git 忽略规则
├── PROJECT_STATE.md                  # 项目状态快照（接手者必看）
├── README.md                         # 本文件
└── CHANGELOG.md                      # 变更日志（待补充）
```

---

## 文档

- 📄 [PROJECT_STATE.md](PROJECT_STATE.md) —— **项目状态快照**（接手者必看）
- 📄 [CHANGELOG.md](CHANGELOG.md) —— 变更日志
- 📄 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) —— 部署指南
- 📄 [docs/OPERATIONS.md](docs/OPERATIONS.md) —— 运维手册
- 🌐 **Swagger UI**：启动后访问 `http://localhost:8080/swagger-ui.html`

---

## 核心功能演示

### 1. 双重确认状态机

```
创建任务          接收方确认         执行方提交         发起方验收
PENDING_ACCEPT ──→ IN_PROGRESS ──→ PENDING_VERIFY ──→ COMPLETED
       │              │                  │
       │              ↓ 驳回             ↓ 驳回
       │           IN_PROGRESS ←──────────┘
       ↓
   WITHDRAWN (发起人撤回)
```

只有接收方"确认接收"时（`PENDING_ACCEPT → IN_PROGRESS`），系统才记录 `actual_start_time` 并推算 `actual_deadline`，**未确认前不计算倒计时或超时**。

### 2. 幂等性保障

```http
POST /api/tasks
Headers:
  X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
  Authorization: Bearer <jwt-token>
Body: { "title": "...", "assigneeId": "..." }
```

24 小时内重复请求**自动返回首次结果**（Redis SETNX + 数据库 UNIQUE 兜底）。

### 3. 敏感数据脱敏

```java
public class User {
    @SensitiveData(type = SensitiveType.ID_CARD)  // 身份证 → "1101234**********"
    private String idCard;
    
    @SensitiveData(type = SensitiveType.MOBILE)     // 手机号 → "138****5678"
    private String mobile;
}
```

权限豁免：
- 超级管理员（ADMIN）默认可见明文
- 用户本人可见自己的明文（基于 `userId` 匹配）
- 其他人看脱敏值，二次确认 + 60 秒自动隐藏

### 4. 可视化关系图

访问 `/visualization` 页面：
- **单人辐射图**：以某用户为中心，展示所有与之有任务往来的人（最多 50 人）
- **多人全景图**：展示 2-50 人之间的任务流转关系

---

## 测试

```bash
# 后端单元测试
cd backend
mvn test

# 前端类型检查 + 构建
cd frontend
npm run type-check
npm run build
```

当前测试覆盖（4 个文件）：
- `IdempotencyAspectTest`（4 case）
- `DesensitizationUtilTest`（6 case）
- `AuditLogAspectTest`（3 case）
- `TaskStateMachineServiceTest`（14 case）

---

## 贡献指南

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交改动：`git commit -m "feat: add your feature"`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

**Commit 规范**：使用 [Conventional Commits](https://www.conventionalcommits.org/)：
- `feat:` 新功能
- `fix:` 修复
- `docs:` 文档
- `refactor:` 重构
- `test:` 测试
- `chore:` 杂项

---

## 许可证

Apache License 2.0
