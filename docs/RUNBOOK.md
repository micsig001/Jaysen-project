# 上线 Runbook

> 适用对象：运维 / SRE / 现场支持
> 最后更新：2026-06-11（项目 HEAD `19c2993`）
> 目标读者：上线上线**当天**值班的人

---

## 一、上线前 1 周准备（PM / 运维）

### 1.1 申请资源

| 资源 | 推荐配置 | 备注 |
|------|---------|------|
| **测试 / UAT 环境** | 1 节点 / 4 CPU / 8GB | 内部 UAT 用 |
| **灰度环境** | 2 节点 / 4 CPU / 8GB | 1% 流量切 1 周 |
| **生产环境** | 3 节点 / 8 CPU / 16GB | 全量 |
| **MySQL 主库** | RDS / 8GB / 主从 | SSD，至少 100GB |
| **Redis** | 4GB / 哨兵模式 | 用于幂等性 / 审计 / 归档锁 |
| **域名 + SSL** | 企微后台配置 OAuth 回调 | cert-manager 自动签 |
| **监控** | Prometheus + Grafana | 见 §5 |
| **日志** | ELK / Loki | 集中化 |

### 1.2 配置企微 OAuth

1. 登录企微管理后台 → **应用管理 → 自建应用**
2. 配置 **OAuth 授权回调域**：`task.example.com`（你的域名）
3. 记录 4 个值到 `.env`：
   - `WEWORK_CORP_ID`（我的企业 → CorpID）
   - `WEWORK_AGENT_ID`（应用详情）
   - `WEWORK_SECRET`（应用 Secret）
   - `FRONTEND_BASE_URL=https://task.example.com`
4. **配置 IP 白名单**：把生产 K8s 出口 IP 加到企微"应用 → 可信 IP"

### 1.3 数据库初始化

```bash
# 1. 创建数据库
mysql -h rds-host -u root -p
> CREATE DATABASE task_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
> CREATE USER 'task_app'@'%' IDENTIFIED BY 'YOUR_STRONG_PASSWORD';
> GRANT ALL ON task_system.* TO 'task_app'@'%';
> FLUSH PRIVILEGES;

# 2. 启动 backend（Flyway 自动建 9 张表 + 索引）
kubectl apply -f k8s/backend-deployment.yaml
kubectl logs -f deployment/backend | grep "Started TaskApplication"

# 3. 创建第一个 ADMIN
mysql -h rds-host -u task_app -p task_system
> INSERT INTO users (user_id, name, role, status, manual_role, created_at, updated_at)
  VALUES ('admin-001', '系统管理员', 'ADMIN', 1, 1, NOW(), NOW());
```

### 1.4 创建管理员账号

参考 §1.3 第 3 步。**或**：用 Swagger `POST /api/auth/token` 走企微 code 换 JWT 流程，让第一个真实企微用户登录后**自动同步**为 EMPLOYEE，然后手动 UPDATE role=ADMIN。

---

## 二、上线当天时间线（4 小时窗口）

### 2.1 T-2h：最终预演（运维）

```bash
# 1. 确认所有 commit 已合并到 main
git log --oneline -5 origin/main

# 2. 确认 GitHub Actions 通过
gh run list --workflow=ci.yml --limit=1

# 3. 灰度环境最后一次部署
kubectl apply -k k8s/
kubectl rollout status deployment/backend --timeout=5m
kubectl rollout status deployment/frontend --timeout=5m

# 4. 关键端点健康
curl https://staging.task.example.com/actuator/health
# 预期: {"status":"UP",...}

# 5. 核心 API 烟雾测试
curl -X POST https://staging.task.example.com/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"code":"WECODE_TEST"}'
# 预期: 200 + access_token（或 401，看是否真连企微）
```

### 2.2 T-1h：流量切换准备

```bash
# 1. 备份当前生产 DB（重要！万一要回滚）
mysqldump -h prod-rds -u root -p task_system > backup-$(date +%Y%m%d-%H%M%S).sql

# 2. 通知所有值班人员
# Slack: "10 分钟后开始灰度发布 v1.0.0"
# 邮件: PM 发"系统升级预告"给全体用户

# 3. 准备回滚脚本（见 §4）
```

### 2.3 T-0：开始灰度（1% 流量）

```bash
# 1. 给生产 deployment 打新版本镜像
kubectl set image deployment/backend backend=registry/task-backend:v1.0.0
kubectl set image deployment/frontend frontend=registry/task-frontend:v1.0.0

# 2. 看滚动更新
kubectl rollout status deployment/backend --timeout=5m
kubectl rollout status deployment/frontend --timeout=5m

# 3. 切 1% 流量（通过 Ingress 或 service mesh）
# 取决于你的 ingress 方案：Nginx-ingress 用 canary annotation
# 或 Argo Rollouts / Flagger
```

### 2.4 T+30min：观察灰度

| 指标 | 正常 | 异常 → 立即回滚 |
|------|------|---------------|
| `/actuator/health` | `UP` | `DOWN` / `OUT_OF_SERVICE` |
| HTTP 5xx 错误率 | < 0.1% | > 1% |
| API P99 延迟 | < 500ms | > 2s |
| 企微消息发送成功率 | > 95% | < 80% |
| 登录成功率 | > 99% | < 95% |

**如何看错误率**：Grafana 面板 / Kibana 查询 `status:>=500` 的 5 分钟 count。

### 2.5 T+2h：扩到 10% → 50% → 100%

每步观察 30 分钟到 1 小时，**没异常**才继续：

```bash
# 5% → 10% → 50% → 100%（每步 30 min 观察）
# Nginx-ingress 切流量示例：
kubectl annotate ingress task-ingress nginx.ingress.kubernetes.io/canary-weight=10
# 30 min 后：
kubectl annotate ingress task-ingress nginx.ingress.kubernetes.io/canary-weight=50
# 再 30 min：
kubectl annotate ingress task-ingress nginx.ingress.kubernetes.io/canary-weight=100
```

### 2.6 T+4h：全量完成

- [ ] 所有 pod Running（`kubectl get pods`）
- [ ] `/actuator/health` 持续 UP 30 分钟
- [ ] Grafana 面板无异常告警
- [ ] 5xx 错误率 < 0.1%
- [ ] 至少 10 个真实用户成功登录

发邮件给老板 + 全员："升级完成 v1.0.0"。

---

## 三、运行监控（值班要看什么）

### 3.1 关键指标（Prometheus 抓 `/actuator/metrics`）

| 指标 | 阈值 | 告警 |
|------|------|------|
| `jvm.memory.used` | < 80% heap | > 90% 持续 5min |
| `jvm.threads.live` | < 500 | > 800 |
| `hikaricp.connections.active` | < 80% pool | > 95% |
| `http.server.requests` P99 | < 1s | > 3s |
| `process.cpu.usage` | < 70% | > 90% 持续 10min |

### 3.2 业务指标（埋点查询）

| 查询 | 正常 |
|------|------|
| 每小时创建任务数 | 50-500（看公司规模） |
| 每小时状态流转数 | 100-1000 |
| 每小时登录用户数 | > 5% 公司总人数 |
| 归档任务数 / 天 | < 100 |

### 3.3 关键日志关键词（Kibana / Loki 告警）

```yaml
- "ERROR" "Business exception"  # 业务异常（一般不要紧）
- "ERROR" "JWT认证处理异常"     # JWT 验证失败（可能是攻击）
- "ERROR" "同步部门异常"        # 企微同步失败
- "WARN" "Redisson"              # Redis 抖动
- "ERROR" "获取分布式锁异常"     # 锁降级（已修 P2.8，会 fail-fast）
```

---

## 四、回滚 SOP（30 分钟内完成）

### 4.1 决策：什么时候回滚

- 灰度 1% / 5% / 10% 任一阶段 5xx 错误率 > 5%
- `/actuator/health` 持续 DOWN 超过 5 分钟
- 数据库连接池耗尽 / OOM
- 用户大量反馈"登录失败"或"看不到任务"

### 4.2 回滚步骤

```bash
# 1. 立即切回 100% 旧版本（最快 30 秒）
kubectl rollout undo deployment/backend
kubectl rollout undo deployment/frontend
kubectl rollout status deployment/backend --timeout=2m

# 2. 确认旧版本 RUNNING
kubectl get pods -l app=backend
# 应该看到 v0.9.x 镜像的 pod 起来

# 3. （如果涉及 DB schema 变更）回滚 Flyway migration：
#    Flyway 不直接支持 down migration，但有 repair + baseline 方案
#    **强烈建议**: 涉及 schema 的 release 之前先在测试环境演练 down migration 脚本
#    联系 DBA 执行：
mysql -h rds-host -u task_app -p task_system
> ALTER TABLE users RENAME COLUMN new_col TO old_col;  -- 视具体 migration 而定

# 4. 通知
# Slack: "已回滚到 v0.9.x，原因：xxx"
# 邮件: "升级临时回滚，预计 24h 内重新发布"
```

### 4.3 事后

- [ ] 写 incident report（5 分钟内出初稿，24h 内出终稿）
- [ ] git tag `v1.0.0-rollback` 标记回滚点
- [ ] 24h 后再评估是否重发

---

## 五、监控接入（运维）

### 5.1 Prometheus 抓 Spring Boot Actuator

`prometheus.yml` 加：
```yaml
scrape_configs:
  - job_name: 'task-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['backend-service:8080']
```

后端 `application.yml` 加：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
  prometheus:
    metrics:
      export:
        enabled: true
```

### 5.2 Grafana 面板（推荐）

导入面板 ID：**4701**（JVM (Micrometer)）+ **11378**（Spring Boot）
或自己建，重点 panel：
- JVM 内存 / 线程
- HikariCP 连接池
- HTTP 请求 P99
- 业务指标（任务创建数 / 登录数）

### 5.3 Sentry 错误告警

`application.yml`：
```yaml
sentry:
  dsn: https://xxx@sentry.io/123
  environment: prod
  enable-tracing: true
```

依赖：
```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter</artifactId>
    <version>7.0.0</version>
</dependency>
```

---

## 六、常见值班问题（FAQ）

### Q1: 用户反馈"看不到任务"
1. 让用户截图错误提示
2. 查 Kibana 是否有 4xx（400/403/404）
3. 查用户部门：`SELECT * FROM users WHERE user_id = 'XXX'`
4. 查任务：是否归档 / 状态

### Q2: 用户反馈"登录失败"
1. 查 Redis：`redis-cli ping`
2. 查企微：管理后台 → 应用 → 是否禁用
3. 查 JWT：让用户重新走 OAuth 流程
4. 查 IP 白名单：生产 K8s 节点 IP 是否在企微白名单

### Q3: 5xx 错误率突然飙升
1. 看 HPA 是否触发（kubectl get hpa）
2. 看 Pod 资源：`kubectl top pods`
3. 看 MySQL 慢查询（slow query log）
4. 必要时回滚（§4）

### Q4: 归档任务失败
1. 看 ArchiveService 日志
2. P2.8 已修：Redis 不可用会 fail-fast
3. 单实例 / 多实例协调：检查 `archive.lock-fail-fast` 配置
4. 手动重跑：`POST /api/sync/trigger`

### Q5: 凌晨 2 点收到告警
- **P0 告警**（系统 DOWN）：立即响应
- **P1 告警**（单个用户问题）：记录，明天看
- **P2 告警**（性能慢但不影响）：记录，下周 review

---

## 七、上线后 1 周

- [ ] 全量监控数据回顾（错误率 / 延迟 / 资源使用）
- [ ] 收集用户反馈（UAT 参与者的 5 分钟演示）
- [ ] 写第一份"周运维报告"（给 PM / 老板）
- [ ] 计划下一迭代（P3 4 项中挑 1-2 项）

---

**附：相关文档**

- [`docs/DEPLOYMENT.md`](DEPLOYMENT.md) —— 部署 4 种方式
- [`docs/DEMO_SCRIPT.md`](DEMO_SCRIPT.md) —— 5 分钟客户演示脚本
- [`docs/FAQ.md`](FAQ.md) —— 用户常见问题
- [`README.md`](../README.md) —— 项目总览
- [`CHANGELOG.md`](../CHANGELOG.md) —— 变更日志
