# Frontend Deliverable — 6 Fixes Summary

6 个 commit 顺序按 `fix(p0.1)` → `fix(p0.3)` → `fix(p0.4)` → `fix(p0.5)` → `fix(p1.6)` → `fix(p1.7)` 提交并 push 到 `origin/main`。

| # | 任务 | Commit | 一句话说明 |
| --- | --- | --- | --- |
| 1 | **P0.1** | `e25d462` | `frontend/package.json` 加 `"type-check": "vue-tsc --noEmit"` 脚本，CI `npm run type-check` 不再失败。 |
| 2 | **P0.3** | `1dbe266` | `axios` 升级到 `^1.17.0`（实际安装 1.17.0），修复 CVE-2023-45857 SSRF 漏洞。 |
| 3 | **P0.4** | `90d4a1c` | `router/index.ts` 把 TODO 替换为：从 JWT payload 解 `userId` → 调 `GET /api/users/{userId}` → 写入 `userStore.userInfo`；`isAdmin/isManager` 不再永远 false。 |
| 4 | **P0.5** | `9688bca` | `workbench/TaskListView.vue` + `TaskDetailView.vue` 全部用 `import request from '@/utils/request'` 替代裸 axios；`grep -r "import axios" frontend/src/views/workbench/` 0 命中。 |
| 5 | **P1.6** | `444a288` | `task/TaskListView.vue` 把 `handleResize` 提到 `onMounted` 外，加 `onUnmounted` 配对 `removeEventListener` 避免 resize listener 内存泄漏（`WorkbenchLayout.vue` / `VisualizationView.vue` 原本已配对）。 |
| 6 | **P1.7** | `b5b4024` | 前端 `request.ts` axios 默认加 `X-Requested-With: XMLHttpRequest`；后端新增 `CsrfHeaderFilter`（`/api/auth/**`、`/api/files/**`、`/actuator/**`、Swagger 白名单 + OPTIONS 预检放行；其余 `/api/**` 缺 header 返回 403）+ 9 个单元测试。**未启用** `CookieCsrfTokenRepository`。 |

## 验证

- **前端**：`cd frontend && npm run type-check && npm run build` — 均通过（0 errors，`built in 10.78s`）。
- **后端**：`cd backend && mvn test` — `Tests run: 145, Failures: 0, Errors: 0, Skipped: 3` `BUILD SUCCESS`。
- **Git push**：`bf5d316..b5b4024 main -> main`（origin/main 已包含全部 6 个 commit）。

详见 `C:\Users\zhang\.mavis\plans\plan_63da86b9\outputs\track-frontend-build-csrf\deliverable.md`。
