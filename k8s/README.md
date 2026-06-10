# Kubernetes 部署（k8s/）

本目录是「企业任务协同管理系统」的 Kubernetes 生产部署清单。
按 Kustomize 组织，可一键 `kubectl apply -k k8s/` 部署。

---

## 目录结构

```
k8s/
├── namespace.yaml              # 命名空间 task-system
├── configmap.yaml              # 非敏感配置（DB/Redis 地址、企微 ID 等）
├── secret.yaml                 # 敏感配置（**占位符**，部署前必须替换）
├── mysql-statefulset.yaml      # MySQL 8.0 StatefulSet + Headless Service
├── redis-deployment.yaml       # Redis 7 Deployment + ClusterIP Service
├── backend-deployment.yaml     # Spring Boot Deployment + Service
├── frontend-nginx-configmap.yaml  # nginx 配置（serve 静态 + 反代 /api）
├── frontend-deployment.yaml    # 前端 Deployment + Service
├── ingress.yaml                # Ingress + (可选) cert-manager ClusterIssuer
├── kustomization.yaml          # Kustomize 入口
└── README.md                   # 本文件
```

---

## 前置要求

| 组件 | 最低版本 | 安装方式 |
|------|---------|---------|
| Kubernetes | 1.24+ | EKS / GKE / AKS / 自建 |
| kubectl | 1.24+ | `az aks install-cli` / `aws eks install` |
| cert-manager | 1.12+ | `kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml` |
| nginx-ingress | 1.9+ | `kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml` |
| Kustomize | 4.5+ | kubectl 1.24+ 已内置 |

> ⚠️ **必装**：cert-manager（用于 Let's Encrypt 自动签发证书）和 nginx-ingress
> （用于 Ingress Controller）。这两个组件不包含在本目录中，请先在集群中部署。

---

## 必填项

部署前必须修改 3 处占位符：

| 字段 | 位置 | 说明 |
|------|------|------|
| `<YOUR_DOMAIN>` | `ingress.yaml` | 真实域名（如 `task.example.com`），DNS 需解析到 ingress LB |
| `<YOUR_CORP_ID>` / `<YOUR_AGENT_ID>` | `configmap.yaml` | 企业微信 CorpID 与 AgentID |
| `<YOUR_FILE_DOWNLOAD_SECRET>` | `configmap.yaml` | 文件下载 HMAC 签名密钥（≥32 字符） |
| `<YOUR_REGISTRY>` | `backend-deployment.yaml` / `frontend-deployment.yaml` / `kustomization.yaml` | 镜像仓库地址（如 `registry.example.com/task`） |

Secret 中的 4 个密码占位符也需要替换真实值（见下一节）。

---

## Secret 填充（**3 种方式**）

### 方式 1：直接编辑 `secret.yaml`（**不推荐**）

```bash
# 把 <BASE64_ENCODED> 替换为真实值（base64 编码后）
echo -n 'YourStrongDBPassword' | base64
# 例：输出 Rm9yU3Ryb25nREJQYXNzd29yZA==
```

**风险**：明文密码进入 Git 仓库历史。

### 方式 2：`kubectl create secret` 直传（**推荐**）

```bash
kubectl create namespace task-system

kubectl -n task-system create secret generic task-secret \
  --from-literal=DB_PASSWORD='YourStrongDBPassword' \
  --from-literal=REDIS_PASSWORD='YourStrongRedisPassword' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=WEWORK_SECRET='YourWeWorkAppSecret' \
  --from-literal=FILE_DOWNLOAD_SECRET="$(openssl rand -base64 32)"

# 跳过 secret.yaml 的 apply
```

### 方式 3：Sealed Secrets / External Secrets（**生产更优**）

```bash
# 1. 安装 Sealed Secrets controller
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system

# 2. 本地准备好明文 secret.yaml，提交前用 kubeseal 加密
kubeseal --format yaml < secret.yaml > sealed-secret.yaml
# 3. 提交 sealed-secret.yaml 即可，CI 中无需明文
```

---

## 一键部署

```bash
# 1. 把所有占位符替换成真实值（参考上节「必填项」）
# 2. 填充 Secret（方式 2 / 3 推荐）
# 3. 一键 apply
kubectl apply -k k8s/

# 观察启动进度
kubectl -n task-system get pods -w
```

> Kustomize 会按以下顺序应用：
> namespace → configmap → secret → mysql（StatefulSet）→ redis → backend
> → frontend-nginx-configmap → frontend → ingress

---

## 验证步骤

```bash
# 1. 检查所有 Pod 是否 Running
kubectl -n task-system get pods
# 预期：mysql-0、redis-xxx、backend-xxx、frontend-xxx 全部 1/1 Running

# 2. 检查 Service / Endpoints
kubectl -n task-system get svc,endpoints

# 3. 验证 MySQL（应自动建库 + Flyway 迁移 9 张表）
kubectl -n task-system exec -it mysql-0 -- mysql -uroot -p task_system -e "SHOW TABLES;"
# 密码用之前 kubectl create secret 时的 DB_PASSWORD

# 4. 验证后端健康
kubectl -n task-system port-forward svc/backend-service 8080:8080
curl http://localhost:8080/actuator/health
# 预期：{"status":"UP",...}

# 5. 验证前端
kubectl -n task-system port-forward svc/frontend-service 8081:80
# 浏览器打开 http://localhost:8081

# 6. 验证 Ingress（DNS 解析正常后）
curl -I https://<YOUR_DOMAIN>/healthz
# 预期：200 ok
```

### 故障排查

| 症状 | 排查 |
|------|------|
| Pod 卡在 `Pending` | 节点资源不足 / PVC 没法绑定：检查 `kubectl describe pod` |
| backend 一直 `CrashLoopBackOff` | 看 `kubectl logs` 末尾的 Spring 异常，常见是数据库密码错 / Flyway 失败 |
| mysql Pod 启动慢 | 首次 init data dir 需要 30-60s，耐心等 startupProbe 通过 |
| 证书没签发 | 检查 `kubectl describe certificate -n task-system`、ClusterIssuer 状态 |
| 前端 502 | 检查 backend 健康：`kubectl -n task-system logs -l app=backend` |

---

## 资源占用

| 组件 | replicas | requests | limits |
|------|----------|----------|--------|
| mysql | 1 | 512Mi / 500m | 1Gi / 1 |
| redis | 1 | 256Mi / 200m | 512Mi / 500m |
| backend | 2 | 512Mi / 500m | 1Gi / 1 |
| frontend | 2 | 128Mi / 100m | 256Mi / 200m |

**节点最小需求（不含 ingress / 系统组件）**：
- CPU: 4 cores (sum of requests ×1.5)
- Memory: 4Gi (sum of requests ×1.5)

---

## 升级流程

```bash
# 1. 改镜像版本（在 deployment yaml 中或 kustomization.yaml 的 images 段）
# 2. 重新部署（Kustomize + RollingUpdate 零停机）
kubectl apply -k k8s/

# 3. 跟踪进度
kubectl -n task-system rollout status deployment/backend
kubectl -n task-system rollout status deployment/frontend
```

---

## 数据备份 / 清理

```bash
# 备份 MySQL
kubectl -n task-system exec mysql-0 -- \
  mysqldump -uroot -p"$DB_PASSWORD" task_system | gzip > backup-$(date +%Y%m%d).sql.gz

# 清理（**会删 PVC 和数据**）
kubectl delete -k k8s/
kubectl delete pvc -n task-system -l app=mysql
```

---

## 与 docker-compose 的差异

| 项 | docker-compose | k8s |
|----|---------------|-----|
| 副本数 | 各 1 | backend / frontend 2 副本（高可用） |
| 数据库 | docker volume | StatefulSet + PVC 20Gi |
| HTTPS | 反向代理自签 | cert-manager + Let's Encrypt |
| 滚动更新 | 手动 restart | RollingUpdate 零停机 |
| 配置管理 | .env 文件 | ConfigMap + Secret |
