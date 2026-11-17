
# 贡献指南

## 范围

NekoSU 只支持**临时 root（越狱模式）**，不接受以下方向的改动：

- 刷写 boot / init_boot 实现永久 root
- 修改内核镜像或绕过签名校验
- 新增在线服务依赖（更新检查除外）

## 开发环境

- JDK 21、Android SDK 36、NDK 29
- Rust nightly（构建 `userspace/ksud`）
- 推荐 Docker 或 GitHub Actions 复现构建

见 [构建文档](docs/BUILDING.md)。

## 代码约定

- Kotlin：随仓库 `spotless` / `ktlint` 配置，提交前本地格式化
- Rust：`cargo fmt`、`cargo clippy`
- C/内核补丁：遵循内核代码风格，最小 diff
- 提交信息使用 `type(scope): summary`（feat/fix/refactor/docs/ci）

## 提交流程

1. Fork 仓库，从 `main` 创建分支
2. 只做单一目的的改动，附带说明与验证方式
3. 本地编译至少通过 `:app:assembleDebug`；涉及内核/ksud 的改动需说明测试内核与机型
4. 发起 PR，描述改动、影响范围、验证结果

## 安全

- 不要在 PR 中提交签名密钥、token、设备私有信息
- 涉及权限、SELinux、su 逻辑的改动需在 PR 中说明威胁模型

## 文档

- 用户可见行为变化必须同步更新 README 或 `docs/`
- 不保留与实现不符的旧文档
