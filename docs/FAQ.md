# 常见问题（FAQ）

> 本文档汇总 10 个最常被问到的问题。如果没找到答案，请提交 [GitHub Issue](https://github.com/micsig001/Jaysen-project/issues)。

---

## 目录

1. [Q1: 没有默认账号，怎么登录？](#q1-没有默认账号怎么登录)
2. [Q2: 企微集成需要哪些配置？](#q2-企微集成需要哪些配置)
3. [Q3: 任务"已撤回"和"已驳回"有什么区别？](#q3-任务已撤回和已驳回有什么区别)
4. [Q4: 幂等性怎么用？会不会导致重复创建？](#q4-幂等性怎么用会不会导致重复创建)
5. [Q5: 敏感数据脱敏的"二次确认"是什么意思？](#q5-敏感数据脱敏的二次确认是什么意思)
6. [Q6: 归档触发后，老数据会不会丢？](#q6-归档触发后老数据会不会丢)
7. [Q7: 移动端能用吗？为什么关系图谱看不到？](#q7-移动端能用吗为什么关系图谱看不到)
8. [Q8: 部署需要什么基础设施？最低成本怎么搭？](#q8-部署需要什么基础设施最低成本怎么搭)
9. [Q9: 出错了怎么看日志？](#q9-出错了怎么看日志)
10. [Q10: 怎么二次开发？比如加个"项目"维度？](#q10-怎么二次开发比如加个项目维度)

---

## Q1: 没有默认账号，怎么登录？

**答：** 应用启动后**没有默认账号**。第一次登录的用户会成为 ADMIN 角色（前提是数据库中还没有任何用户记录）。整个登录流程是：

1. 在企业微信后台创建一个自建应用，配置回调域名为你的服务域名。
2. 员工在企微工作台点击该应用 → 企微回调到后端 → 后端用 `code` 换取 `userid` → 自动注册账号 + 签发 JWT。
3. 前端拿到 JWT（加密后存 localStorage）→ 跳转进系统。

如果你是**本地开发**没有企微环境，后端会 fallback 到 **FakeWeWorkApiClient**（仅 dev profile），自动生成一个固定 userid 登录。具体配置见 [docs/DEPLOYMENT.md](DEPLOYMENT.md) 的"本地开发"章节。

---

## Q2: 企微集成需要哪些配置？

**答：** 需要在 `.env`（或 `application.yml`）里配置以下 4 个值，全部来自企微管理后台：

| 变量名 | 怎么拿 |
|--------|--------|
| `WEWORK_CORP_ID` | 我的企业 → 企业信息 → CorpID |
| `WEWORK_AGENT_ID` | 应用管理 → 自建应用 → AgentId |
| `WEWORK_SECRET` | 同上 → Secret（**不要提交到 Git**） |
| `WEWORK_REDIRECT_URI` | 同上 → 可信域名，**必须是 HTTPS**（本地开发可用内网穿透如 ngrok） |

配置完重启服务，员工就能从企微工作台免登录进入。

> ⚠️ **常见坑：** `WEWORK_SECRET` 泄露到 Git 后必须立刻在企微后台重置，因为任何拿到 secret 的人都能模拟该应用回调。

---

## Q3: 任务"已撤回"和"已驳回"有什么区别？

**答：** 两者都是终态，但触发方和语义完全不同：

| 状态 | 触发方 | 触发时机 | 后续动作 |
|------|--------|---------|----------|
| **WITHDRAWN（已撤回）** | 发起方 | 任务在 `PENDING_ACCEPT`（待接收）时，发起方主动取消 | 执行方再也看不到这条任务，但审计日志保留 |
| **REJECTED（已驳回）** | 发起方（验收时） | 任务在 `PENDING_VERIFY`（待验收）时，发起方觉得质量不达标 | 任务回到 `IN_PROGRESS`，执行方重新做 |

简单记：**撤回 = 发起方说"我不派了"，驳回 = 发起方说"你重做"**。

业务上可以根据这两个状态做统计：
- 撤回率高 → 派活质量问题
- 驳回率高 → 执行质量或需求不清晰

---

## Q4: 幂等性怎么用？会不会导致重复创建？

**答：** 不会。系统在三个层面做了防重：

1. **客户端** —— 调用方传入 `X-Idempotency-Key: <UUID>` header（推荐自动生成）。
2. **Redis** —— 后端用 `SETNX` 把 key 存 24 小时，重复请求直接返回首次结果。
3. **MySQL** —— `idempotency_key` 字段加 `UNIQUE` 约束，作为最终兜底。

**典型用法**（前端示例）：

```ts
import { v4 as uuidv4 } from 'uuid'

const idempotencyKey = uuidv4()

await axios.post('/api/tasks', payload, {
  headers: { 'X-Idempotency-Key': idempotencyKey }
})
```

**好处：**
- 用户在弱网下多点了几次"提交"按钮 → 不会创建多条任务。
- 移动端 App 被杀进程重试 → 安全。
- 定时任务补偿重发 → 不会污染数据。

**坑点：** 24 小时内同样的 key 会一直命中；如果业务逻辑希望"第二次就报错"，需要在 header 之外再加业务校验（如订单号唯一）。

---

## Q5: 敏感数据脱敏的"二次确认"是什么意思？

**答：** 默认情况下，所有标注了 `@SensitiveData` 的字段（身份证、手机号、银行卡、薪资等）**对非本人/非管理员**显示脱敏值（如 `138****5678`）。要看明文需要：

1. 点击字段 → 弹窗显示 **"是否确认查看？"**
2. 用户点击确认 → 后端记录审计日志（谁看了谁的什么字段）→ 返回明文
3. 前端展示明文 → **60 秒后自动隐藏**

**豁免规则：**
- 超级管理员（ADMIN）默认看明文
- 用户看自己的字段看明文

**为什么这么设计？**
- 防误操作：别人查工资时多看一眼需要"明示意图"
- 防滥用：审计日志记录了"张三在 14:32:15 看了李四的手机号"，事后可追溯

**配置位置：** 见 `backend/src/main/java/com/task/privacy/DesensitizationUtil.java`。

---

## Q6: 归档触发后，老数据会不会丢？

**答：** 不会丢。归档是**物理迁移**到 `task_archive` 表，不是删除。

**归档规则（默认）：**
- 任务创建时间 > 1 年
- 状态为终态：`COMPLETED` / `WITHDRAWN` / `REJECTED`
- 两个条件同时满足才归档

**触发方式：**
- 定时任务：每天凌晨 3:00 跑一次（可在 `application.yml` 调整 cron）
- 手动触发：管理员在"历史任务"页右上角点 **手动归档** 按钮

**好处：**
- 主表（`task`）保持轻量，查询性能不下降
- 老数据完整保留在 `task_archive` 表，仍可查可导出

**回滚：** 如果发现归档错误，把数据从 `task_archive` 拷回 `task` 即可（需要先调整归档时间规则避免再次被归档）。

---

## Q7: 移动端能用吗？为什么关系图谱看不到？

**答：** 大部分功能移动端可用，关系图谱**主动屏蔽**了移动端，原因是：

- ECharts 力导向图在手机小屏上节点重叠严重，体验差
- PC 端的鼠标 hover/拖拽交互在触屏上不好用

**路由层的处理：** `frontend/src/router/index.ts` 里 `visualization` 路由有 `meta: { pcOnly: true }`，宽度 < 768px 时自动跳到 `/mobile-restricted` 提示页。

**移动端可用功能：**
- ✅ 任务列表 / 详情 / 创建
- ✅ 历史记录查看
- ✅ 登录 / 退出
- ❌ 关系图谱（建议改用 PC）
- ❌ 管理后台（不建议在手机上改角色/启停账号）

**为什么不做 PWA：** 企微工作台本身就是个 Web 容器，员工不需要单独安装 App。

---

## Q8: 部署需要什么基础设施？最低成本怎么搭？

**答：** 最低配置（适合 50 人以下小团队 Demo / 试用）：

| 组件 | 最低规格 | 月成本估算（云） |
|------|---------|----------------|
| 应用服务器 | 2C4G | ¥50-100 |
| MySQL | 1C2G / 20GB SSD | ¥30-80 |
| Redis | 1G 内存版 | ¥20-50 |
| **合计** | | **¥100-230/月** |

**推荐部署架构：**

```
[ 员工企微 ] → [ Nginx (HTTPS) ] → [ Spring Boot (单实例) ]
                                       │
                                       ├─→ [ MySQL 8.0 ]
                                       └─→ [ Redis 6+ ]
```

**生产建议（500 人）：**
- Spring Boot 2 实例 + Nginx 负载均衡
- MySQL 主从 + 每日全量备份
- Redis 哨兵或 Cluster
- 对象存储（OSS/S3）放附件 —— 当前版本 attachment 是 TODO，预计 v1.1 加上

**详细部署步骤：** 见 [docs/DEPLOYMENT.md](DEPLOYMENT.md)。

---

## Q9: 出错了怎么看日志？

**答：** 三个层面：

### 1. 应用日志（最常用）
```bash
# 后端日志（默认输出到控制台，docker 环境输出到 docker logs）
docker-compose logs -f backend

# 前端日志：浏览器 DevTools Console / Network
```

### 2. 业务审计日志
- 路径：管理后台 → 审计日志（仅 ADMIN 可见）
- 记录：谁、什么时候、改了什么字段、改前值、改后值
- 用于排查"谁把我的任务改没了"

### 3. 系统错误
- 路径：`logs/spring.log`（默认在应用根目录）
- Spring Boot 默认输出 ERROR 级别，可调整 `application.yml` 的 `logging.level.com.task` 提高到 DEBUG

**常用定位命令：**

```bash
# 找最近的 ERROR
grep -i "ERROR" logs/spring.log | tail -50

# 找特定用户的所有操作
grep "userId=lihua" logs/spring.log
```

**调试技巧：** 开启 SQL 日志可以看慢查询：
```yaml
# application.yml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

---

## Q10: 怎么二次开发？比如加个"项目"维度？

**答：** 加维度是常见的扩展，思路如下：

### 后端改动（约 1-2 天）

1. **新增 `project` 表**
   ```sql
   CREATE TABLE project (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     name VARCHAR(100) NOT NULL,
     code VARCHAR(50) UNIQUE,
     owner_id VARCHAR(50),
     created_at DATETIME
   );
   ```

2. **`task` 表加 `project_id` 字段**（写 Flyway V4 迁移）

3. **新增 `ProjectController` / `ProjectService` / `ProjectMapper`**
   - 参照 `TaskController` 的结构，CRUD + 分页 + 权限过滤

4. **`Task` 实体加 `projectId` 字段**，DTO 透传到前端

5. **审计日志 / 脱敏 / 幂等性**：现有 AOP 注解直接复用

### 前端改动（约 0.5-1 天）

1. **新增 `frontend/src/api/project.ts`**
2. **新增 `frontend/src/views/project/ProjectListView.vue`**
3. **路由表注册 + 侧边栏菜单加入口**
4. **任务创建/详情页加"所属项目"下拉框**

### 权限适配

如果"项目"也按角色隔离，需要扩展 `TaskVisibilityService`：
- EMPLOYEE：看自己 + 同事参与的项目
- MANAGER：看本部门所有项目
- ADMIN：看全公司

**进阶：** 如果希望"项目"也支持树形（多级子项目），参考 `department` 表的设计（parent_id 自引用）。

完整二次开发文档待补充（[GitHub Issue #TODO](https://github.com/micsig001/Jaysen-project/issues)）。

---

## 📮 没找到答案？

- 🐛 **Bug 报告**：[GitHub Issues](https://github.com/micsig001/Jaysen-project/issues/new?template=bug_report.md)
- 💡 **功能建议**：[GitHub Issues](https://github.com/micsig001/Jaysen-project/issues/new?template=feature_request.md)
- 💬 **使用咨询**：提交 Issue 时打 `question` label
- 📧 **商务合作**：见 README 底部的联系方式

> 我们承诺工作日 24 小时内首次响应 Issue。