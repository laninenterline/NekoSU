# Contributing to NekoSU

Thanks for your interest! This project is a **temporary-root-only** KernelSU manager fork
（临时 root / 越狱模式），不提供 boot 镜像安装与永久 root。

## 开发环境

- JDK 21、Android SDK、Rust nightly、Android NDK r29
- 构建步骤见 [docs/BUILDING.md](docs/BUILDING.md)

## 代码结构

- `kernel/`：内核模块（Kbuild/兼容层）
- `kernel/susfs/`：SUSFS LKM（vendored）
- `userspace/ksud/`：Rust 守护进程
- `manager/`：Android Manager（Compose，MVVM）
- `uapi/`：共享 ABI 头文件

## 提交规范

使用 Conventional Commits：

```text
feat(scope): ...
fix(scope): ...
docs: ...
build(scope): ...
ci: ...
refactor(scope): ...
```

## PR 检查清单

- [ ] 不引入永久 root / boot patch 相关功能或文案
- [ ] Manager 构建通过：`cd manager && ./gradlew :app:assembleDebug`
- [ ] ksud 构建通过
- [ ] 涉及内核改动时说明 KMI 与构建方式
- [ ] 同步更新 `CHANGELOG.md` / `docs/`
- [ ] 不提交 keystore、密码、私人路径、构建产物（除预编译 ko）

## 文档规范

- 只描述临时 root（越狱模式）能力，不宣称永久 root
- 命令使用代码块，路径使用相对路径
- 新增页面需在 `docs/README.md` 登记
