# 部署一页纸（Quick Reference）

> **目标读者**：被问"这系统怎么部署"时直接发这一页。
> **完整文档**：[`DEPLOYMENT.md`](DEPLOYMENT.md)（4 种方式详细步骤）+ [`RUNBOOK.md`](RUNBOOK.md)（上线当天 SOP）
> **适用版本**：v1.0.0（HEAD `12a8f89`）

---

## 1 分钟决策

| 场景 | 推荐方案 | 文档 |
|------|---------|------|
| **个人试一下 / Demo 给老板看** | Docker Compose（一条命令） | [§2](#2-docker-compose一键起-2-分钟) |
| **公司内部测试 / UAT** | 本地源码跑（IDE 调试） | [§3](#3-本地源码跑-开发团队-10-分钟) |
| **正式上线（推荐）** | Kubernetes | [`k8s/README.md`](../k8s/README.md) |
| **没 K8s 但要正式跑** | 虚拟机 + Docker Compose | [§2 + 配 nginx 反代](#2-docker-compose一键起-2-分钟) |

---

## 2. Docker Compose（一键起，2 分钟）

**前置**：Docker Desktop（4GB+ 内存分配）

```bash
# 1. 拉代码
git clone https://github.com/micsig001/Jaysen-project.git
cd Jaysen-project

# 2. 配环境变量
cp .env.example .env
# 编辑 .env 必改 3 个：
#   DB_PASSWORD=YourStrongPassword
#   REDIS_PASSWORD=YourStrongPassword
#   JWT_SECRET=$(openssl rand -base64 32)  # 至少 32 字符

# 3. 启动 4 个容器
docker compose up -d

# 4. 等待 30 秒（MySQL + Flyway + Backend）
docker compose logs -f backend | grep "Started TaskApplication"

# 5. 验证
curl http://localhost:8080/actuator/health
# 预期: {"status":"UP",...}
```

**访问入口**：
- 前端：http://localhost
- Swagger API 文档：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/actuator/health
- Prometheus 指标：http://localhost:8080/actuator/prometheus

**创建第一个管理员**：
```sql
docker exec -it task-mysql mysql -uroot -p task_system
> INSERT INTO users (user_id, name, role, status, manual_role, created_at, updated_at)
  VALUES ('admin-001', '系统管理员', 'ADMIN', 1, 1, NOW(), NOW());
```

**停服 / 清数据**：
```bash
docker compose down       # 停容器（保数据）
docker compose down -v    # 停容器 + 删数据卷
```

---

## 3. 本地源码跑（开发团队，10 分钟）

**前置**：JDK 17 + Maven 3.9 + Node.js 20 + Docker

```bash
# 1. 只起 MySQL + Redis（用 Docker）
docker compose up -d mysql redis

# 2. 跑后端（新终端）
cd backend
mvn spring-boot:run
# 首次会下 200+ 依赖，等 5-10 分钟

# 3. 跑前端（新终端）
cd frontend
npm install      # 首次等 2 分钟
npm run dev      # 默认 http://localhost:5173
```

**优势**：改代码 hot reload（前端秒级，后端 ~5s）  
**生产配置**：`--spring.profiles.active=prod`（会启用 MySQL SSL + 关 Swagger）

---

## 4. Kubernetes（正式生产）

**前置**：K8s 1.24+ + cert-manager + nginx-ingress

```bash
# 一键部署
kubectl apply -k k8s/

# 跟踪
kubectl -n task-system get pods -w
kubectl -n task-system get ingress
```

**详细配置 / 必填项替换 / Secret 注入方式** → [`k8s/README.md`](../k8s/README.md)

**资源占用**（3 节点最小生产）：

| 组件 | replicas | requests | limits |
|------|----------|----------|--------|
| MySQL | 1 | 512Mi / 500m | 1Gi / 1 |
| Redis | 1 | 256Mi / 200m | 512Mi / 500m |
| Backend | 2 | 512Mi / 500m | 1Gi / 1 |
| Frontend | 2 | 128Mi / 100m | 256Mi / 200m |
| **节点最小** | — | — | **4 CPU / 4Gi Mem** |

---

## 5. 必改的 5 个环境变量（所有方式通用）

| 变量 | 必须改？ | 怎么生成 |
|------|---------|---------|
| `DB_PASSWORD` | ✅ | 强密码 ≥16 字符 |
| `REDIS_PASSWORD` | ✅ | 强密码 ≥16 字符 |
| `JWT_SECRET` | ✅ | `openssl rand -base64 32`（≥32 字符） |
| `FILE_DOWNLOAD_SECRET` | 强烈推荐 | 同上（独立于 JWT） |
| `WEWORK_CORP_ID` / `AGENT_ID` / `SECRET` | 上线必填 | 企微管理后台 |

**生成 JWT_SECRET（PowerShell）**：
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }) -join "")
```

**生产禁用 Swagger**（默认已关）：`SWAGGER_ENABLED=true` 可临时打开演示

---

## 6. 部署后 6 项验证

```bash
# 1. 后端健康
curl http://localhost:8080/actuator/health
# 预期: {"status":"UP",...}

# 2. 前端 HTML（应返回 Vue SPA shell）
curl -I http://localhost

# 3. Swagger 文档（生产应 404）
curl http://localhost:8080/swagger-ui.html

# 4. 数据库表（应看到 9 张）
docker exec task-mysql mysql -uroot -p -e "USE task_system; SHOW TABLES;"

# 5. Redis 联通
docker exec task-redis redis-cli -a "YourStrongRedisPassword" ping
# 预期: PONG

# 6. 跑完整测试（项目自带 152 个测试）
cd backend
mvn test
# 预期: 152 通过 / 0 失败 / 3 跳过
```

---

## 7. 出问题先看这里

| 症状 | 99% 是这原因 | 解决 |
|------|------------|------|
| 8080 端口被占用 | 旧服务 / 别的项目占着 | `netstat -ano \| findstr :8080` + kill |
| MySQL 连不上 | 没等 30 秒 Flyway 跑完 | 看 `docker logs task-mysql` |
| 前端 502 | 后端没起来 | 查 backend health |
| Swagger 404 | 生产 profile（正常） | `SWAGGER_ENABLED=true` 重启 |
| 登录提示 401 | 企微 corp 配置错 | 查环境变量 + 企微后台 |
| 看不到任务 | 数据权限隔离（MANAGER 只看本部门） | 换 ADMIN 账号 |
| 测试 152 → 全失败 | JDK 版本不对 | 必须 **JDK 17**（不能 11/21） |

---

## 8. 完整文档索引

| 文档 | 用途 | 受众 |
|------|------|------|
| **本文档**（QUICKSTART_DEPLOY.md） | 1 分钟决策 + 4 种部署 | 所有人 |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | 4 种方式详细步骤 | 实施人员 |
| [`RUNBOOK.md`](RUNBOOK.md) | 上线当天 SOP（监控 / 告警 / 回滚） | 值班运维 |
| [`k8s/README.md`](../k8s/README.md) | K8s 详细（Secret 注入 / 镜像 tag / HPA） | K8s 实施 |
| [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) | 5 分钟客户演示走查 | 销售 / 售前 |
| [`FAQ.md`](FAQ.md) | 用户常见 10 个问题 | 用户支持 |
| [`CHANGELOG.md`](../CHANGELOG.md) | 版本变更历史 | 所有人 |
| [`README.md`](../README.md) | 项目总览 + 状态 + 文档导航 | 所有人 |

---

**适用版本**：v1.0.0（main 分支 HEAD `12a8f89`）  
**最后更新**：2026-06-11
