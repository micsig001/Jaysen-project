# 运维手册 (Operations Manual)

本文档面向运维工程师，介绍系统的日常运维操作。

## 目录

- [服务管理](#服务管理)
- [监控告警](#监控告警)
- [日志管理](#日志管理)
- [常见问题排查](#常见问题排查)
- [灾备与恢复](#灾备与恢复)
- [扩容方案](#扩容方案)

---

## 服务管理

### 查看服务状态

```bash
# Docker Compose 方式
docker-compose ps

# Kubernetes 方式
kubectl get pods -n task-system
```

### 重启服务

```bash
# Docker Compose
docker-compose restart backend         # 仅重启后端
docker-compose up -d --force-recreate  # 强制重建（会丢失内存数据）

# Kubernetes
kubectl rollout restart deployment/backend -n task-system
```

### 查看资源占用

```bash
# Docker
docker stats task-backend task-frontend task-mysql task-redis

# K8s
kubectl top pods -n task-system
```

### 进入容器调试

```bash
# 后端
docker exec -it task-backend sh

# 数据库
docker exec -it task-mysql mysql -uroot -p task_system
```

---

## 监控告警

### 关键指标（建议接入 Prometheus + Grafana）

| 指标 | 阈值 | 告警级别 |
|------|------|----------|
| Backend CPU | > 80% 持续 5 分钟 | 警告 |
| Backend 内存 | > 85% | 警告 |
| Backend P99 响应时间 | > 1s | 警告 |
| MySQL 连接数 | > 80% max_connections | 警告 |
| Redis 内存 | > 80% maxmemory | 警告 |
| 磁盘空间 | > 85% | 严重 |
| 同步任务失败 | 最近 24h 内 ≥ 1 次 | 严重 |
| 归档任务失败 | 最近 30 天内 ≥ 1 次 | 严重 |

### 健康检查端点

> **状态**：Spring Boot Actuator **待集成**。当前没有 `/actuator/health` 端点。

**临时方案**：
```bash
# 检查后端响应
curl -f http://localhost:8080/swagger-ui.html || echo "BACKEND_DOWN"

# 检查前端
curl -f http://localhost:5173 || echo "FRONTEND_DOWN"
```

**正式方案**（推荐接入）：

1. 加 `spring-boot-starter-actuator` 依赖
2. 启用端点：`management.endpoints.web.exposure.include=health,info,metrics,scheduledtasks`
3. 配置详细健康检查（数据库/Redis 状态）

### 业务告警

| 场景 | 通知方式 |
|------|----------|
| 任务超时（>24h 未完成） | 企微消息提醒（**待实现**） |
| 归档任务失败 | 企微群机器人 |
| 数据库连接失败 | 短信 + 企微 |
| 系统错误率 > 1% | 企微群机器人 |

---

## 日志管理

### 日志位置

```bash
# Docker 容器日志
docker logs task-backend > backend.log 2>&1
docker logs task-mysql > mysql.log 2>&1

# 应用日志（容器内）
docker exec task-backend ls -la /app/logs/
```

### 日志格式

应用日志使用 SLF4J + Logback，输出到 stdout（被 Docker 收集）。

格式：
```
2026-06-10 12:34:56 [http-nio-8080-exec-1] INFO  com.task.service.TaskService - [任务] 创建任务: id=123, taskNo=T20260610-001, title=...
```

### 日志级别调整

```bash
# 临时调整（重启后失效）
docker exec task-backend sh -c "echo 'logging.level.com.task=DEBUG' >> /app/config/application-debug.yml"
docker restart task-backend

# 永久调整：编辑 application.yml
```

### 日志切割

容器日志默认不会自动切割，长期运行会撑爆磁盘。**生产环境必须配置**：

```bash
# Docker Compose 配置（修改 daemon.json）
cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "10"
  }
}
EOF

systemctl restart docker
```

**已有容器**的日志需要先 rotate：
```bash
# 清空旧日志（注意：先备份！）
truncate -s 0 $(docker inspect --format='{{.LogPath}}' task-backend)
```

### 日志分析

```bash
# 看最近 1 小时 ERROR 日志
docker logs --since=1h task-backend 2>&1 | grep ERROR

# 看最慢的 10 个请求
docker logs task-backend | grep "took" | sort -k 5 -n -r | head -10
```

---

## 常见问题排查

### 问题 1: 用户登录失败

**症状**：点击"企业微信登录"无反应或报错

**排查清单**：
- [ ] 后端日志：`docker logs task-backend | grep "企微"`
- [ ] 企微后台"应用主页"是否配置：`https://your-domain.com/api/auth/wework/callback`
- [ ] 企微后台"可见范围"是否包含该用户
- [ ] 企微 corp_id / agent_id / secret 是否正确
- [ ] 防火墙是否放行 443 端口
- [ ] SSL 证书是否有效（企微要求 HTTPS）

**快速测试**：
```bash
# 直接测试回调端点
curl -v "https://your-domain.com/api/auth/wework/callback?code=test"
```

### 问题 2: 任务创建报 500

**排查**：
```bash
# 看后端详细错误
docker logs --tail=100 task-backend | grep -A 20 "ERROR"
```

**常见原因**：
- 幂等性 Key 重复使用 → 重新生成 UUID
- 数据库连接断开 → 检查 MySQL
- 字段验证失败 → 看 400 错误（不是 500）

### 问题 3: 定时任务没执行

**检查**：
```bash
# 1. 看 Spring 定时任务线程是否在跑
docker logs task-backend | grep "scheduling"

# 2. 看时区
docker exec task-backend date
# 必须 Asia/Shanghai (CST)

# 3. 手动触发（如果实现了手动接口）
curl -X POST http://localhost:8080/api/archive/trigger \
  -H "Authorization: Bearer <admin-token>"
```

### 问题 4: 前端白屏

**排查**：
```bash
# 1. 看前端是否响应
curl -I http://localhost:5173

# 2. 看前端 build 产物
docker exec task-frontend ls -la /usr/share/nginx/html/

# 3. 看 nginx 日志
docker logs task-frontend

# 4. 看浏览器控制台
# F12 → Console / Network
```

### 问题 5: 接口慢

**排查步骤**：
```sql
-- 1. MySQL 慢查询
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;
SHOW VARIABLES LIKE 'slow_query_log%';

-- 2. 当前正在执行的查询
SELECT * FROM information_schema.PROCESSLIST WHERE COMMAND != 'Sleep' ORDER BY TIME DESC LIMIT 20;
```

```bash
# 3. Redis 慢查询
docker exec task-redis redis-cli SLOWLOG GET 10
```

### 问题 6: 数据不一致

**场景**：任务表和状态历史表数据对不上

**修复**：
```sql
-- 找出状态历史缺失的任务
SELECT t.id, t.task_no, t.status, t.created_at
FROM tasks t
LEFT JOIN task_status_history h ON h.task_id = t.id
WHERE h.id IS NULL;
```

如果是迁移期间丢失，需要从 `audit_log` 表反推。

---

## 灾备与恢复

### 备份策略

| 数据 | 备份频率 | 保留期 | 方式 |
|------|----------|--------|------|
| MySQL | 每日 2:00 | 30 天 | mysqldump + 压缩 |
| Redis | 不备份 | - | 丢失只影响幂等性 |
| 上传文件 | 每周 | 90 天 | rsync 到异地 |

### 数据库恢复

```bash
# 1. 停止应用（防新数据写入）
docker-compose stop backend frontend

# 2. 恢复
gunzip < /var/backups/task_system_20260610.sql.gz | \
  docker exec -i task-mysql mysql -uroot -p task_system

# 3. 重启
docker-compose up -d backend frontend

# 4. 验证
docker exec task-mysql mysql -uroot -p task_system -e "SELECT COUNT(*) FROM users;"
```

### Redis 重启

Redis 数据丢失影响：
- 幂等性 Key 丢失 → 客户端 24h 内可能产生重复请求（**不致命**，数据库兜底）
- 缓存丢失 → 第一次请求会稍慢（缓存重建）
- 分布式锁丢失 → 极端情况下归档可能并发执行（**需要人工确认**）

**操作**：
```bash
docker restart task-redis
# 数据从 RDB 恢复（如有）或 AOF
```

### 极端情况：从零恢复

```bash
# 1. 拉取代码
git clone https://github.com/micsig001/Jaysen-project.git
cd Jaysen-project

# 2. 恢复 .env（从备份的 secret 管理）
cp /secure-backup/.env .

# 3. 启动服务
docker-compose up -d

# 4. 恢复数据库
gunzip < /var/backups/task_system_latest.sql.gz | \
  docker exec -i task-mysql mysql -uroot -p task_system

# 5. 触发企微用户同步
curl -X POST http://localhost:8080/api/sync/trigger \
  -H "Authorization: Bearer <admin-token>"
```

**RTO（恢复时间目标）**：30 分钟
**RPO（数据丢失目标）**：24 小时（每日 2 点备份）

---

## 扩容方案

### 阶段 1: 单机（< 50 并发用户）

```
所有服务在一台 4C8G 机器：
- MySQL: 2C4G
- Redis: 1C1G
- Backend: 1C2G
- Frontend (Nginx): 0.5C0.5G
```

### 阶段 2: 读写分离（50-200 并发用户）

```
主从 MySQL：
  - 主库：写入
  - 从库：读取（应用层通过 AOP 或中间件路由）
  
Backend：水平扩展（2-3 个实例 + Nginx 负载均衡）

Redis：保持单实例（缓存不需要集群）
```

### 阶段 3: 微服务拆分（> 200 并发用户）

按业务域拆分：
- 认证服务（auth-service）
- 任务服务（task-service）
- 归档服务（archive-service）
- 可视化服务（visualization-service）

每个服务独立数据库 + K8s 部署。

### 数据库扩容指标

| 指标 | 阈值 | 行动 |
|------|------|------|
| CPU > 70% 持续 | 1 小时 | 升级到 4C8G |
| 连接数 > 80% | - | 增加 max_connections + 优化连接池 |
| QPS > 5000 | - | 读写分离 |
| 单表 > 1000 万行 | - | 分表（按 created_at 月份） |

### 缓存策略

| 数据 | 缓存时长 | 失效策略 |
|------|----------|----------|
| 用户信息 | 10 分钟 | 写时失效 |
| 部门树 | 1 小时 | 写时失效 |
| 字典数据 | 24 小时 | 定时刷新 |
| 任务详情 | 5 分钟 | 状态变更时失效 |

**当前实现**：`WeWorkSyncService.syncUsers()` 使用 `selectByUserIds` 批量查询，但没有缓存层。**建议**：高频读路径加 Redis 缓存。

---

## 应急联系

| 角色 | 联系人 | 电话 |
|------|--------|------|
| 系统负责人 | - | - |
| DBA | - | - |
| 运维 | - | - |
| 安全应急 | - | - |

> 建议在生产前完善此表格。

---

## 升级检查清单

每次发版前必须检查：

- [ ] Flyway 迁移脚本已测试（dev / staging 环境）
- [ ] 数据库有当天备份
- [ ] 前端构建产物已测试
- [ ] 镜像版本号已更新
- [ ] CHANGELOG 已更新
- [ ] 监控告警规则已更新
- [ ] 应急预案已就位
- [ ] 已通知相关方（提前 24h）

---

## 性能调优清单

### MySQL

```sql
-- 查看慢查询
SHOW VARIABLES LIKE 'slow_query_log%';

-- 查看表状态
SHOW TABLE STATUS FROM task_system;

-- 查看索引使用情况
SELECT * FROM sys.schema_index_statistics 
WHERE table_schema = 'task_system' 
ORDER BY rows_selected DESC LIMIT 10;
```

### Redis

```bash
# 看内存分布
docker exec task-redis redis-cli --bigkeys

# 看命中率
docker exec task-redis redis-cli info stats | grep keyspace
```

### JVM

```bash
# Heap dump
docker exec task-backend jmap -dump:format=b,file=/tmp/heap.bin <pid>
docker cp task-backend:/tmp/heap.bin .

# 线程 dump
docker exec task-backend jstack <pid> > thread-dump.txt
```

---

## 已知运维风险

1. **当前没有正式监控系统**（Prometheus + Grafana 待接入）
2. **没有日志聚合系统**（ELK / Loki 待接入）
3. **没有告警通知**（企微 / 短信 / 邮件）
4. **Spring Boot Actuator 未启用**（健康检查端点缺失）
5. **Docker 镜像未做漏洞扫描**（建议用 Trivy）
6. **数据库无主从**（单点故障）

**建议优先级**：
1. 接入 Prometheus + Grafana（最重要）
2. 启用 Spring Boot Actuator
3. 配置 Docker 日志切割
4. MySQL 主从 + 自动故障转移
