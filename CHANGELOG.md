# 变更日志 (CHANGELOG)

> 记录项目所有重要变更。每次会话的代码改动都会在这里汇总。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

---

## [Unreleased] — 2026-06-10

### ⚠️ 重要说明

本次会话为**首次入仓**（initial commit），所有变更一次性合入。下次发布版本（v1.0.0）会基于本次内容。

### Added（新增）

#### 核心功能
- **企业级任务协同管理系统** —— 项目从零搭建
- **企业微信 OAuth2.0 登录** —— `GET /api/auth/wework/callback` 回调端点
- **JWT 认证** —— 签发 / 校验 / 刷新 / 注销（Redis 黑名单）
- **任务双重确认状态机** —— `PENDING_ACCEPT → IN_PROGRESS → PENDING_VERIFY → COMPLETED`，含 `REJECTED` / `WITHDRAWN` 旁路
- **部门/用户增量同步** —— 基于 `update_time` Upsert 逻辑，每日凌晨 2 点定时执行
- **任务超时检查** —— `TaskTimeoutScheduler` 每日凌晨 1 点扫描超时任务

#### 三个 AOP（核心创新点）
- **Idempotency AOP** (`@Idempotent`)：X-Idempotency-Key + Redis SETNX + 数据库 UNIQUE 三重机制
- **AuditLog AOP** (`@AuditLog`)：异步写入 audit_log 表，捕获方法前后数据快照
- **SensitiveData AOP** (`@SensitiveData`)：6 种脱敏规则（NAME/ID_CARD/BANK_CARD/MOBILE/EMAIL/SALARY），权限豁免（ADMIN 直显 + 本人可见明文）

#### 归档模块
- **ArchiveService** —— 分批归档（默认 1000 条/批），Redis 分布式锁（防多实例重复），Redis 防御性记录（防重复迁移）
- **ArchiveScheduler** —— 每月 1 号凌晨 3 点定时归档
- **ArchiveController** —— 手动触发 + 历史任务分页查询 + 待归档数统计
- **数据权限过滤** —— 按角色（EMPLOYEE 仅自己 / MANAGER 本部门 / ADMIN 全部）自动过滤可见范围

#### 可视化（最小版）
- **VisualizationService** —— 单人辐射图（50 人上限按任务数排序）+ 多人全景图
- **VisualizationController** —— REST 接口
- **VisualizationView.vue** —— ECharts Graph force 布局渲染，支持缩放/拖拽/点击

#### Swagger / OpenAPI
- **springdoc-openapi 2.3.0** 集成
- **SwaggerConfig** —— Bearer 认证配置
- 所有 Controller 添加 `@Tag` / `@Operation` 注解

#### 数据库
- **V1** —— 9 张表建表（users / departments / tasks / task_status_history / sync_log / audit_log / tasks_history_archive / idempotency_keys）
- **V2** —— 6 个复合索引（`idx_assignee_status` / `idx_creator_status` / `idx_status_created` / `idx_deadline_status` / `idx_task_created` / `idx_operator_time`）
- **V3** —— 归档表优化索引（3 个：creator_archived / title 前缀 / original_created）

#### 测试
- **IdempotencyAspectTest**（4 case：首次/重复/异常/缺 Key）
- **DesensitizationUtilTest**（6 case：所有脱敏规则）
- **AuditLogAspectTest**（3 case：成功/异常/SpEL 资源 ID）
- **TaskStateMachineServiceTest**（14 case：5 个状态流转方法全覆盖）

#### 前端
- **SensitiveText.vue** —— 脱敏展示组件（二次确认 + 60 秒自动隐藏）
- **permission.ts** —— `v-permission` 指令（按角色显示/隐藏 DOM）
- **HistoryView.vue** 重写：完整表单 + 表格 + 分页 + 详情弹窗
- **TaskCreateView.vue** 修复：creatorId 从 userStore 读取
- **TaskListView.vue** 修复：状态值与后端对齐
- **TaskDetailView.vue** 修复：字段名 + API 导入
- **WorkbenchLayout.vue** 修复：退出登录正确清除加密 token
- **VisualizationView.vue** 重写：ECharts Graph 真实图表

#### 文档
- **README.md** —— 完整重写（项目介绍 / 技术栈 / 快速启动 / 架构 / 演示）
- **PROJECT_STATE.md** —— 项目状态快照（接手者必看）
- **.env.example** —— 完善环境变量模板
- **.gitignore** —— 200+ 规则
- **.gitattributes** —— 统一换行符为 LF

### Changed（变更）

- **TaskController** 重构：Controller 不再直接调 Mapper，通过 TaskService
- **TaskService 新增**：CRUD 层 + 数据权限过滤
- **application.yml** 新增：`archive.*` / `app.frontend-base-url` 配置块
- **SecurityConfig** 变更：CORS 按环境配置（默认仅 localhost，通过 `ALLOWED_ORIGINS` 扩展）
- **GlobalExceptionHandler** 完善：BusinessException / Validation / AccessDenied 统一处理

### Fixed（修复）

#### 审计自审发现（11 个 P0/P1）

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| 归档 B1 | 🔴 P0 | 批次删除用归档表自增 ID 删主表（InnoDB 独立表空间下不共享）→ 误删数据 | 改用 `task_no` 关联删除 |
| 归档 B2 | 🔴 P0 | V3 SQL 降序索引不兼容 MySQL 5.7 | 砍掉，依赖 V1 索引 + 反向扫描 |
| 归档 B3 | 🔴 P0 | SensitiveDataAspect 异常时返回明文 | 异常时 data=null + 500 |
| 归档 B4 | 🔴 P0 | IdempotencyAspect 反序列化丢失泛型 | 用 JavaType 保留泛型 |
| 归档 B5 | 🔴 P0 | AuditLogAspect resourceId 默认取第一个参数误判 | 改扫描 `@PathVariable` 标注 |
| 归档 B6 | 🔴 P0 | TaskService CRUD 缺 `@Transactional` | 全部加注解 |
| 归档 M5 | 🟡 P1 | AuditLogAspect 入参含框架对象序列化失败 | 过滤 `@PathVariable/@RequestParam/@RequestBody` |
| 归档 M6 | 🟡 P1 | SensitiveDataAspect 不处理父类字段 | 递归向上 `getSuperclass()` |
| 归档 M7 | 🟡 P1 | SensitiveDataAspect 无循环引用检测 | `IdentityHashMap` 防御 |
| 归档 M8 | 🟡 P1 | IdempotencyAspect 抛 raw RuntimeException | 抛 429 业务异常 |
| 归档 M9 | 🟡 P1 | TaskController 缺 `@Valid` 校验 | 实体加 `@NotBlank` 等注解 + Controller `@Valid` |
| 归档 M10 | 🟡 P1 | `isPrimitiveLike` 逻辑混乱 | 简化 |
| 归档 m7 | 🟢 P2 | X-Forwarded-For 拿整段字符串 | 取第一个 IP |
| 归档 m8 | 🟢 P2 | isOwnerField 检查 `id` 太宽泛 | 只匹配 `userId` |
| 归档 m9 | 🟢 P2 | 单字符姓名原样返回 | 强制 `"*"` |
| 归档 m10 | 🟢 P2 | 没校验 UUID 格式 | UUID v4 正则 |

#### 上线前必做（4 项 + 1 个 P0 bug）

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| 1 | 🔴 P0 | TaskService 缺数据权限过滤 | `applyDataPermissionFilter` + `hasDataPermission` |
| 2 | 🔴 P0 | 缺企微 OAuth 回调接口 | `GET /api/auth/wework/callback` + SecurityConfig 放行 |
| 3 | 🔴 P0 | TaskStateMachineService 0 测试 | 14 case 单元测试 |
| 4 | 🔴 P0 | 缺前端脱敏组件 + 权限指令 | `SensitiveText.vue` + `v-permission` |
| 5 | 🔴 P0 | 状态历史实体 `oldStatus/newStatus` 与 V1 SQL 字段 `from_status/to_status` 不匹配 → 写库失败 | 实体改名 `fromStatus/toStatus`，Service 同步 |

#### 外部 review 发现（12 项）

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| C1 | 🔴 CRITICAL | `User.setAvatar()` 编译失败 | 改 `setAvatarUrl()`（实体字段名） |
| C2 | 🔴 CRITICAL | 前端 `cancelTask` API 未导出 | api/task.ts 加 `cancelTask`/`withdrawTask` |
| C3 | 🔴 CRITICAL | `task.initiatorId` 永远为空 | 改 `task.creatorId`（与后端字段对齐） |
| C4 | 🔴 CRITICAL | 状态值 `PENDING_REVIEW`/`CANCELLED` 不存在 | 改 `PENDING_VERIFY`/`WITHDRAWN` |
| C5 | 🔴 CRITICAL | 定时任务用 `PENDING_REVIEW` → 超时不预警 | 改 `PENDING_VERIFY` |
| H1 | 🔴 HIGH | 可视化图谱仅占位 | 完整实现（Service + Controller + ECharts 渲染） |
| H2 | 🔴 HIGH | `creatorId` 为空违反 NOT NULL | 从 `useUserStore` 读取 + 提交兜底 |
| M1 | 🟡 MEDIUM | 退出登录用错误 key | 用 `removeToken()` + 兜底清掉 4 个 key |
| M2 | 🟡 MEDIUM | AES 密钥硬编码 fallback | 启动时强制 `VITE_TOKEN_SECRET` ≥ 32 字符 |
| M3 | 🟡 MEDIUM | CORS 全通配 | 按环境配置：默认仅 localhost |
| L1 | 🟢 LOW | 列表默认排序不合理 | 改 `优先级 ASC → 截止时间 ASC → 创建时间 DESC` |
| L2 | 🟢 LOW | 测试断言 `name("张") → "张"` 自相矛盾 | 改 `→ "*"`（与 m9 修复一致） |

### Removed（移除）

- 无

### Deprecated（弃用）

- 无

---

## 历史里程碑

| 时间 | 事件 |
|------|------|
| 2026-06-09 | 旧版代码存在（VERIFICATION_REPORT.md 记录 10 项 P0/P1 已修） |
| 2026-06-10 | 本次会话：项目从零搭建（部分）+ 三个 AOP 完整实现 + 归档审计 + 11+12 项修复 + 入仓 GitHub |

---

## 待办（下次会话）

按优先级排列：

### P0（必做）

- [ ] 跑一次 `mvn clean compile` 验证编译通过
- [ ] 跑一次 `docker-compose up -d` 端到端验证

### P1（强烈建议）

- [ ] `docs/DEPLOYMENT.md` —— 部署指南
- [ ] `docs/OPERATIONS.md` —— 运维手册
- [ ] `ArchiveServiceTest` —— 归档模块单元测试
- [ ] 对象存储实现（`storage/` 包）—— 4 个文件
- [ ] 用户/角色管理（`user/` 包）—— 8 个文件

### P2（建议）

- [ ] GitHub Actions CI
- [ ] main 分支保护
- [ ] K8s 部署清单
- [ ] Spring Boot Actuator
- [ ] 限流（防爆破）

### P3（可选）

- [ ] 任务超时预警（企微消息）
- [ ] 数据库敏感字段（V4 SQL 预留）
- [ ] 集成测试 + E2E 测试
