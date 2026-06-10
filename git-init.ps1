# ============================================
# 首次 Git 初始化 + 首次提交脚本（Windows PowerShell）
# ============================================
# 用法：
#   1) 装好 Git（https://git-scm.com 或 winget install Git.Git）
#   2) 配置 Git 用户名邮箱（如果还没配过）：
#        git config --global user.name "你的名字"
#        git config --global user.email "your.email@example.com"
#   3) 打开 PowerShell，cd 到 E:\Project\Task
#   4) 运行：.\git-init.ps1
#
# 推送远程仓库（可选）：
#   git remote add origin <your-repo-url>
#   git push -u origin main
# ============================================

$ErrorActionPreference = "Stop"
Set-Location "E:\Project\Task"

Write-Host "==> 1. 初始化 Git 仓库..." -ForegroundColor Green
git init
git branch -M main  # 默认主分支改为 main

Write-Host "==> 2. 配置项目级 .gitattributes（统一换行符）..." -ForegroundColor Green
@"
# 强制换行符为 LF（避免 Windows 提交时混入 CRLF）
* text=auto eol=lf

# Windows 批处理文件保留 CRLF
*.bat text eol=crlf
*.cmd text eol=crlf
*.ps1 text eol=crlf
"@ | Out-File -FilePath ".gitattributes" -Encoding UTF8 -NoNewline

Write-Host "==> 3. 添加所有文件..." -ForegroundColor Green
git add .

Write-Host "==> 4. 检查待提交文件数量..." -ForegroundColor Green
$fileCount = (git diff --cached --numstat | Measure-Object).Count
Write-Host "    待提交文件数: $fileCount" -ForegroundColor Yellow

if ($fileCount -eq 0) {
    Write-Host "==> 没有文件需要提交" -ForegroundColor Red
    exit 1
}

Write-Host "==> 5. 提交（首次 commit）..." -ForegroundColor Green
git commit -m @"
chore: initial commit

- 后端：Spring Boot 3 + MyBatis-Plus + Spring Security (JWT) + Redis + MySQL
- 前端：Vue 3 + TypeScript + Element Plus + ECharts + Pinia
- 部署：Docker Compose

功能模块：
- 企业微信 OAuth2.0 登录 + 部门/用户增量同步
- 任务管理：双重确认状态机（PENDING_ACCEPT → IN_PROGRESS → PENDING_VERIFY → COMPLETED）
- 三级权限隔离（EMPLOYEE / MANAGER / ADMIN）+ 数据权限过滤
- 三个 AOP：Idempotency（X-Idempotency-Key 幂等性）+ AuditLog（异步审计）+ SensitiveData（敏感数据脱敏）
- 归档策略：双重防雷（创建时间 > 1年 AND 状态为终态）
- 可视化：单人辐射图 + 多人全景图（ECharts Graph）

测试：JUnit 5 + Mockito
文档：Swagger / OpenAPI 3.0
"@

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "首次提交完成！" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步（可选）：推送远程仓库" -ForegroundColor Yellow
Write-Host "  git remote add origin <your-repo-url>"
Write-Host "  git push -u origin main"
Write-Host ""
Write-Host "查看提交：" -ForegroundColor Yellow
Write-Host "  git log --oneline"
Write-Host "  git status"
