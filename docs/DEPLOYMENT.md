# 部署指南 (Deployment Guide)

本文档介绍企业级任务协同管理系统的各种部署方式。

## 目录

- [方式一：Docker Compose 一键部署（推荐）](#方式一docker-compose-一键部署推荐)
- [方式二：本地开发模式（前后端分离）](#方式二本地开发模式前后端分离)
- [方式三：Kubernetes 部署（生产）](#方式三kubernetes-部署生产)
- [方式四：传统虚拟机部署](#方式四传统虚拟机部署)
- [常见问题](#常见问题)

---

## 方式一：Docker Compose 一键部署（推荐）

### 前置要求

- Docker Engine 20.10+
- Docker Compose 2.0+
- 至少 2GB 可用内存
- 至少 5GB 可用磁盘

### 部署步骤

#### 1. 克隆代码

```bash
git clone https://github.com/micsig001/Jaysen-project.git
cd Jaysen-project
```

#### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，**必须**修改以下项：

```bash
# === 必填 ===

# JWT 密钥（至少 32 字符）
JWT_SECRET=$(openssl rand -base64 32)

# Redis 密码（生产环境请使用强密码）
REDIS_PASSWORD=YourStrongRedisPassword123!

# 数据库密码
DB_PASSWORD=YourStrongDBPassword123!

# 前端 Token 加密密钥（至少 32 字符）
VITE_TOKEN_SECRET=$(openssl rand -base64 32)

# === 企业微信配置 ===

# 从企业微信管理后台"应用管理"获取
WEWORK_CORP_ID=ww1234567890abcdef
WEWORK_AGENT_ID=1000001
WEWORK_SECRET=YourAppSecretHere
```

**⚠️ 安全提示**：
- 所有密钥**不能**用默认值
- `.env` 文件**已经在 .gitignore 中**，不会提交到 Git
- 生产环境**必须**使用强随机密码

#### 3. 配置 CORS

如需允许特定域名访问，编辑 `docker-compose.yml` 或在 `.env` 中添加：

```bash
ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com
```

#### 4. 启动服务

```bash
# 启动（后台运行）
docker-compose up -d

# 查看启动日志
docker-compose logs -f backend

# 验证所有服务健康
docker-compose ps
```

预期输出：

```
NAME                STATUS              PORTS
task-mysql          Up (healthy)        0.0.0.0:3306->3306/tcp
task-redis          Up (healthy)        0.0.0.0:6379->6379/tcp
task-backend        Up (healthy)        0.0.0.0:8080->8080/tcp
task-frontend       Up (healthy)        0.0.0.0:5173->80/tcp
```

#### 5. 初始化数据库（首次部署）

数据库迁移由 Flyway 自动执行：

- 启动 backend 时自动执行 V1__create_tables.sql
- 启动 backend 时自动执行 V2__add_composite_indexes.sql
- 启动 backend 时自动执行 V3__archive_optimization.sql

验证迁移成功：

```bash
docker exec -it task-mysql mysql -uroot -p task_system -e "SHOW TABLES;"
```

应该看到 9 张表（users / departments / tasks / task_status_history / sync_log / audit_log / tasks_history_archive / idempotency_keys）。

#### 6. 验证应用

- **前端**：[http://localhost:5173](http://localhost:5173)
- **后端 Swagger UI**：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **后端健康检查**：[http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)（待补 actuator）

#### 7. 第一个用户登录

首次登录没有默认账号：

1. 在企业微信管理后台，进入"应用管理"→ 找到你的应用
2. 点击"应用主页"，URL 应为 `https://your-domain.com`
3. **首次登录的用户自动获得 ADMIN 角色**（代码逻辑：数据库无记录时创建为 ADMIN）

---

## 方式二：本地开发模式（前后端分离）

适用于开发调试。

### 后端

```bash
# 前置：JDK 17 + Maven 3.9
cd backend

# 1. 启动 MySQL 和 Redis（用 Docker 仅起这两个）
docker-compose up -d mysql redis

# 2. 修改 application.yml 中的数据库/Redis 地址（如需要）
# 默认 localhost:3306 / localhost:6379

# 3. 启动
mvn spring-boot:run

# 或用 IDE 启动（推荐，支持断点调试）
# IntelliJ IDEA: 打开 TaskApplication.java → 右键 → Run
```

### 前端

```bash
# 前置：Node 18+
cd frontend

# 1. 安装依赖
npm install

# 2. 配置 .env
cp ../.env.example .env
# 修改 VITE_API_URL=http://localhost:8080
# 修改 VITE_TOKEN_SECRET=<至少32字符>

# 3. 启动开发服务器
npm run dev

# 浏览器打开 http://localhost:5173
```

### 验证端到端

1. 浏览器打开 `http://localhost:5173`
2. 应该看到登录页
3. 点击"企业微信登录"按钮 → 跳转到企微授权页
4. 授权后回到前端，自动获取 JWT 存储

---

## 方式三：Kubernetes 部署（生产）

> **状态**：K8s 部署清单**待补充**。当前 docker-compose 适合中小规模（< 100 并发用户）。
> 如果需要 K8s 部署，请参考以下步骤手动调整。

### 架构

```
                    ┌─────────────────┐
                    │   Ingress (Nginx)│
                    │   TLS Termination│
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
        ┌─────▼─────┐                 ┌─────▼─────┐
        │  Frontend │                 │  Backend  │
        │  (nginx + │                 │  (Spring  │
        │  static)  │                 │  Boot)    │
        └───────────┘                 └─────┬─────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                          │                 │                 │
                    ┌─────▼─────┐    ┌─────▼─────┐    ┌──────▼──────┐
                    │   MySQL   │    │   Redis   │    │ Object Store│
                    │  (StatefulSet) │ (Deployment) │  (MinIO/S3)  │
                    └───────────┘    └───────────┘    └─────────────┘
```

### 需要准备的资源

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: task-system
```

```yaml
# configmap.yaml - 配置
apiVersion: v1
kind: ConfigMap
metadata:
  name: task-config
  namespace: task-system
data:
  DB_HOST: mysql-service
  DB_PORT: "3306"
  DB_NAME: task_system
  REDIS_HOST: redis-service
  REDIS_PORT: "6379"
  WEWORK_CORP_ID: <your-corp-id>
  WEWORK_AGENT_ID: <your-agent-id>
  SPRING_PROFILE: prod
```

```yaml
# secret.yaml - 敏感信息
apiVersion: v1
kind: Secret
metadata:
  name: task-secret
  namespace: task-system
type: Opaque
stringData:
  DB_PASSWORD: <base64-encoded>
  REDIS_PASSWORD: <base64-encoded>
  JWT_SECRET: <base64-encoded-32字符以上>
  WEWORK_SECRET: <base64-encoded>
```

```yaml
# mysql-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql
  namespace: task-system
spec:
  serviceName: mysql-service
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: task-secret
              key: DB_PASSWORD
        - name: MYSQL_DATABASE
          value: "task_system"
        volumeMounts:
        - name: mysql-data
          mountPath: /var/lib/mysql
  volumeClaimTemplates:
  - metadata:
      name: mysql-data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 20Gi
```

```yaml
# backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: task-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: backend
        image: <your-registry>/task-backend:latest
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: task-config
        env:
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: task-secret
              key: DB_PASSWORD
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: task-secret
              key: REDIS_PASSWORD
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: task-secret
              key: JWT_SECRET
        - name: WEWORK_SECRET
          valueFrom:
            secretKeyRef:
              name: task-secret
              key: WEWORK_SECRET
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

### 部署命令

```bash
# 1. 创建命名空间
kubectl apply -f namespace.yaml

# 2. 配置和密钥
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml

# 3. 数据库（首次）
kubectl apply -f mysql-statefulset.yaml
kubectl apply -f mysql-service.yaml

# 4. Redis
kubectl apply -f redis-deployment.yaml

# 5. 后端
kubectl apply -f backend-deployment.yaml
kubectl apply -f backend-service.yaml

# 6. 前端
kubectl apply -f frontend-deployment.yaml
kubectl apply -f frontend-service.yaml

# 7. Ingress
kubectl apply -f ingress.yaml

# 8. 验证
kubectl get pods -n task-system
kubectl logs -n task-system -l app=backend
```

---

## 方式四：传统虚拟机部署

> 适合无 Docker 环境的场景。

### 步骤

1. **准备服务器**：Ubuntu 22.04+ / CentOS 8+
2. **安装 Java 17**：
   ```bash
   sudo apt install openjdk-17-jdk
   ```
3. **安装 MySQL 8.0**：
   ```bash
   sudo apt install mysql-server
   sudo mysql_secure_installation
   ```
4. **安装 Redis**：
   ```bash
   sudo apt install redis-server
   ```
5. **安装 Nginx**：
   ```bash
   sudo apt install nginx
   ```
6. **部署后端**：
   ```bash
   # 上传 backend/target/*.jar
   sudo java -jar task-backend.jar --spring.profiles.active=prod
   ```
7. **部署前端**：
   ```bash
   # 上传 frontend/dist/*
   # 配置 Nginx serve 静态文件 + 反代 /api 到后端 8080
   ```

### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /var/www/task-frontend;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反代
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Swagger UI
    location /swagger-ui/ {
        proxy_pass http://localhost:8080;
    }
}
```

---

## 常见问题

### Q1: 启动时数据库连接失败

**症状**：`Communications link failure`

**排查**：
```bash
docker-compose ps mysql
docker-compose logs mysql
```

**常见原因**：
1. MySQL 还没启动健康，backend 重试机制不够 → 等几秒后 `docker-compose restart backend`
2. 数据库密码错误 → 检查 `.env` 中的 `DB_PASSWORD`

### Q2: 前端报 "缺少 VITE_TOKEN_SECRET"

**原因**：`.env` 没配置或值太短（< 32 字符）

**修复**：
```bash
echo "VITE_TOKEN_SECRET=$(openssl rand -base64 32)" >> frontend/.env
docker-compose restart frontend
```

### Q3: 登录后跳转失败

**症状**：企微 OAuth 回调后白屏

**排查**：
1. 检查 `app.frontend-base-url` 是否正确（必须前端能访问的 URL）
2. 检查企微后台"应用主页"是否配置为 `https://your-domain.com/api/auth/wework/callback`
3. 查看 backend 日志：`docker-compose logs backend | grep "企微 OAuth"`

### Q4: Flyway 迁移失败

**症状**：`Migration V1__create_tables.sql failed`

**排查**：
```bash
docker exec -it task-mysql mysql -uroot -p task_system -e "SELECT * FROM flyway_schema_history;"
```

**修复**：
- 如果是脏数据：`docker-compose down -v` 删数据卷重启（**生产慎用**）
- 如果是索引冲突：手动 DROP 冲突索引后重启

### Q5: 归档定时任务没跑

**排查**：
```bash
docker exec task-backend curl http://localhost:8080/actuator/scheduledtasks
```

**或者看日志**：
```bash
docker-compose logs backend | grep "归档"
```

**确认 cron 表达式**：`0 0 3 1 * ?` 表示每月 1 号 3 点。

### Q6: 性能问题

**症状**：接口慢 / 超时

**排查步骤**：
1. 检查 MySQL 慢查询日志
2. 检查 Redis 内存使用：`docker exec task-redis redis-cli info memory`
3. 检查 JVM 堆内存：`docker stats task-backend`
4. 调整 backend JVM 参数（修改 docker-compose.yml）：
   ```yaml
   environment:
     JAVA_OPTS: "-Xms512m -Xmx2g -XX:+UseG1GC"
   ```

---

## 升级流程

### 升级后端

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建镜像（如果有 Dockerfile 改动）
docker-compose build backend

# 3. 重启（会执行 Flyway 迁移）
docker-compose up -d backend

# 4. 验证
docker-compose logs -f backend
```

### 数据库迁移

Flyway 自动处理，向后兼容即可。**破坏性变更**（如删除列）需要：
1. 先在测试环境验证
2. 备份生产数据库
3. 写回滚脚本
4. 低峰期执行

---

## 备份策略

### 数据库每日备份

```bash
#!/bin/bash
# /opt/backup/task-db-daily.sh
BACKUP_DIR=/var/backups/task-system
DATE=$(date +%Y%m%d)
mkdir -p $BACKUP_DIR

docker exec task-mysql mysqldump -uroot -p$DB_PASSWORD task_system | gzip > $BACKUP_DIR/task_system_$DATE.sql.gz

# 保留 30 天
find $BACKUP_DIR -name "task_system_*.sql.gz" -mtime +30 -delete
```

加 crontab：
```bash
0 2 * * * /opt/backup/task-db-daily.sh
```

### Redis 备份

Redis 用作缓存，**不需要备份**（丢失只会影响幂等性，幂等性有数据库兜底）。

---

## 性能基准

| 场景 | 配置 | 期望 QPS | P95 延迟 |
|------|------|----------|----------|
| 任务列表查询 | 单 backend + MySQL 8 | ≥ 200 | < 200ms |
| 任务创建（含幂等性） | 单 backend + Redis | ≥ 500 | < 100ms |
| 状态机流转 | 单 backend | ≥ 1000 | < 50ms |
| 归档（1000 条/批） | 单 backend | N/A | < 30s/批 |

> **注**：以上是设计目标，**实际未跑过压测**。建议上线前用 JMeter 验证。
