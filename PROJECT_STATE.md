# PROJECT_STATE — 项目状态快照

> 用途：接手者 / 后续会话快速理解"项目到了哪一步、还差什么、踩过哪些坑"。
> 最后更新：2026-06-10

---

## 1. 基本信息

- **项目**：企业级任务协同管理系统
- **本地路径**：`E:\Project\Task`
- **GitHub**：https://github.com/micsig001/Jaysen-project
- **当前 commit**：`5c9c9b4`（已 push）
- **主分支**：`main`
- **技术栈**：
  - 后端：Spring Boot 3.2.0 + MyBatis-Plus 3.5.5 + Spring Security (JWT) + Redis + MySQL 8.0
  - 前端：Vue 3 + TypeScript + Element Plus + ECharts 5 + Pinia
  - 部署：Docker Compose（mysql:8.0 + redis:7-alpine + backend + frontend）
  - Java：17，Maven 3.9

---

## 2. 已完成的功能（约 70%）

### 核心模块 ✅
- [x] Spring Boot 工程骨架 + 多环境配置
- [x] Flyway V1/V2/V3 数据库迁移（建表 + 索引 + 归档优化）
- [x] JWT 认证 + 企业微信 OAuth2.0 登录（`/api/auth/wework/callback`）
- [x] 部门/用户增量同步（`WeWorkSyncService` 每日 2 点）
- [x] 双重确认任务状态机：`PENDING_ACCEPT → IN_PROGRESS → PENDING_VERIFY → COMPLETED`（含 REJECT/WITHDRAWN）
- [x] 三个 AOP：
  - `@Idempotent`（X-Idempotency-Key + Redis SETNX + 数据库兜底）
  - `@AuditLog`（异步写入 audit_log）
  - `@SensitiveData`（6 种脱敏规则 + 权限豁免 + 嵌套对象 + 循环引用检测）
- [x] 归档模块（双防雷：created_at < 1年 AND status ∈ {COMPLETED, WITHDRAWN}）
- [x] 分布式锁（Redis SETNX + Lua 原子释放）
- [x] Swagger / OpenAPI 3.0（springdoc-openapi 2.3.0）
- [x] 任务 Service 层重构（Controller 不再直调 Mapper）
- [x] 可视化最小版（单人辐射图 + 多人全景图 + ECharts Graph force 布局）

### 单元测试（4 个文件）
- [x] `IdempotencyAspectTest`（4 case）
- [x] `DesensitizationUtilTest`（6 case）
- [x] `AuditLogAspectTest`（3 case）
- [x] `TaskStateMachineServiceTest`（14 case）

---

## 3. 待完成的功能（约 30%）

### 整模块缺失
- [ ] **对象存储**（`storage/` 包）—— 4 个文件未实现
  - `StorageService` 接口
  - `LocalStorageService` / `MinioStorageService` / `AliyunOssStorageService` 三实现
  - 通过 `storage.type` 配置动态注入
  - **影响**：任务附件功能不可用
  - **优先级**：P1（如果不需要附件可后置）

- [ ] **用户/角色管理**（`user/` 包）—— 8 个文件未实现
  - 后端：`UserService` / `UserController` / `RoleService`
  - 端点：`GET /api/users/{userId}/role`、`PUT /api/users/{userId}/role`
  - 前端：`AdminView.vue`（当前是 395 字节占位）
  - **影响**：管理员无法手动调整用户角色
  - **优先级**：P1

### 测试覆盖
- [ ] `ArchiveServiceTest`（双防雷逻辑复杂，需要覆盖）
- [ ] 集成测试（MockMvc）
- [ ] E2E 测试（Cypress / Playwright）

### 文档
- [ ] `README.md` 升级（5/19 旧版）—— 项目介绍 + 快速启动 + 架构图
- [ ] `docs/DEPLOYMENT.md` —— Docker Compose 部署
- [ ] `docs/OPERATIONS.md` —— 监控告警 / 日志 / 灾备 / 扩容
- [ ] `docs/API.md` —— REST API 文档（Swagger 已生成 OpenAPI JSON）
- [ ] `CHANGELOG.md` —— 记录每次会话的改动

### 运维 / CI/CD
- [ ] GitHub Actions CI（自动 `mvn compile` + 前端 `npm run build`）
- [ ] main 分支保护（防 force push）
- [ ] K8s 部署清单（`deployment.yaml` / `service.yaml` / `ingress.yaml`）
- [ ] 限流（防爆破）
- [ ] Spring Boot Actuator（健康检查端点）

### P3 优化
- [ ] 数据库敏感字段（id_card / bank_account / salary）—— V4 SQL 预留
- [ ] 任务超时预警（企微消息提醒）—— 文档标记"可选"

---

## 4. 已知风险

### ⚠️ 高风险

1. **整个项目没真编译过**（Maven / Java 当时未安装）
   - 即使我 self-review 修了 11 个 P0/P1 + 12 个外部 review 项，仍可能有编译错误
   - **建议**：装 JDK 17 + Maven 后跑一次 `mvn clean compile` + `npm run build`

2. **Docker Compose 端到端没跑过**
   - Flyway V1/V2/V3 迁移脚本未经实战检验
   - 索引可能冲突 / 启动顺序问题 / 健康检查超时

3. **很多关键修复来自外部 review**（不是我自己发现）
   - 5 个 CRITICAL 都是"用户视角"的硬伤，作者视角看不出来
   - **建议**：所有 CRITICAL 修复后做一次集成测试

### ⚠️ 中风险

4. **JWT Token 加密**（`crypto.ts`）
   - 修复后启动时**强制要求** `VITE_TOKEN_SECRET`（≥32 字符）
   - 如果用户没配 `.env`，前端启动失败
   - **状态**：已加 `.env.example` 提示

5. **CORS 配置**
   - 修复后默认仅允许 localhost，通过 `ALLOWED_ORIGINS` 环境变量配置
   - 生产部署**必须**配置 `ALLOWED_ORIGINS=https://your-domain.com`

6. **状态历史字段名 P0 bug**（已修）
   - 之前 `TaskStateMachineService` 写 `setOldStatus()` / `setNewStatus()`，但 V1 SQL 字段是 `from_status` / `to_status`
   - 修复：实体改为 `fromStatus` / `toStatus`，Service 同步
   - **影响范围**：所有状态流转的审计追溯

7. **批次删除 ID 混淆 P0 bug**（已修）
   - 之前 `archiveBatch` 用归档表自增 ID 删主表，但 InnoDB 独立表空间下自增 ID 不共享
   - 修复：改用 `task_no` 关联删除
   - **影响范围**：归档功能

---

## 5. Git 仓库现状

```
remote: https://github.com/micsig001/Jaysen-project.git
branch: main (tracking origin/main)
HEAD:   5c9c9b4

.gitignore:    已写（200+ 规则）
.gitattributes: 已写（强制 LF 换行）

首次 commit 内容：
  - chore: initial commit
  - 104 个文件（+ 51 个 objects）
  - 包含后端 11 个包、前端所有 view、新增的 AOP / Service / Test

Push 已成功（user 跑 `git push -u origin main --force`）
```

---

## 6. 重要文件索引（接手者必看）

| 文件 | 说明 |
|------|------|
| `backend/pom.xml` | 依赖 + Java 17 + Maven 3.9 |
| `backend/src/main/resources/application.yml` | 全局配置（含 archive.* sensitive-data.*） |
| `backend/src/main/resources/db/migration/` | Flyway V1/V2/V3 |
| `backend/src/main/java/com/task/TaskApplication.java` | 启动类（`@EnableAsync`） |
| `backend/src/main/java/com/task/auth/` | JWT 认证 |
| `backend/src/main/java/com/task/controller/` | 4 个 Controller |
| `backend/src/main/java/com/task/service/` | TaskService + TaskStateMachineService |
| `backend/src/main/java/com/task/archive/` | 归档模块（ArchiveService + Controller + Scheduler） |
| `backend/src/main/java/com/task/idempotency/` | 幂等性 AOP |
| `backend/src/main/java/com/task/audit/` | 审计 AOP |
| `backend/src/main/java/com/task/privacy/` | 脱敏 AOP |
| `backend/src/main/java/com/task/visualization/` | 可视化（Service + Controller） |
| `frontend/src/views/` | 7 个 View（login/admin/task/workbench/visualization/history） |
| `frontend/src/api/` | 5 个 API 封装（auth/task/archive/visualization） |
| `frontend/src/components/SensitiveText.vue` | 前端脱敏组件 |
| `frontend/src/directives/permission.ts` | `v-permission` 指令 |
| `docker-compose.yml` | 4 服务编排 |
| `.env.example` | 环境变量模板（含必填项 VITE_TOKEN_SECRET / REDIS_PASSWORD / JWT_SECRET） |

---

## 7. 启动命令

```bash
# 后端（需 JDK 17 + Maven）
cd backend
mvn spring-boot:run

# 前端（需 Node 18+）
cd frontend
npm install
cp ../.env.example .env  # 改 VITE_TOKEN_SECRET
npm run dev

# Docker 一键起
docker-compose up -d
```

---

## 8. 下一步优先级建议

| 优先级 | 工作 | 原因 |
|-------|------|------|
| **P0** | 跑一次 `mvn clean compile` | 验证编译通过 |
| **P0** | 跑一次 `docker-compose up -d` | 端到端验证 |
| **P1** | 写 README + CHANGELOG | 项目介绍 |
| **P1** | 写 DEPLOYMENT + OPERATIONS 文档 | 运维交接 |
| **P1** | 写 ArchiveService 单元测试 | 双防雷逻辑 |
| **P2** | GitHub Actions CI | 防回归 |
| **P2** | 对象存储 | 任务附件 |
| **P2** | 用户/角色管理 | Admin 功能 |
| **P3** | K8s / 限流 / 监控 | 生产加固 |
