# ============================================
# 一键 Push 脚本（用户自己跑）
# ============================================
# 用法：
#   1) 在你自己 PowerShell（**交互模式**）里跑这一行
#   2) 如果第一次推，Git 会弹认证框
#   3) Username: micsig001
#   4) Password: 你的 GitHub PAT（不是登录密码）
#   5) 配了 GCM 后，token 会加密缓存到 Windows 凭据管理器，以后 push 不用再输
# ============================================

# 配置 GCM（首次）
git config --global credential.helper manager

# Push 到 GitHub
git -C "E:\Project\Task" push -u origin main
