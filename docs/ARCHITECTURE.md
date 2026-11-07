# 架构说明

## 组成

| 目录 | 说明 |
|------|------|
| `kernel/` | KernelSU 内核模块源码，按 `kernel/Makefile` 的 objs 列表构建 |
| `kernel/susfs/` | 随仓库分发的 SUSFS LKM 源码（4.x 预编译 ko 由 CI 交叉编译） |
| `userspace/ksud/` | Rust 编写的 `ksud` 守护进程，内嵌各 KMI 的预编译 ko |
| `userspace/ksuinit/` | `ksuinit` 模块 |
| `manager/` | Android 管理器（Compose） |
| `uapi/` | 内核、ksud、管理器共享的 UAPI 头文件 |
| `scripts/` | 本地/CI 构建脚本（`build_kmi.sh` 等） |
| `tools/` | APK 重打包与集成工具 |
| `bindings/js/` | JavaScript 绑定 |

## 启动链

1. boot image 中集成 KernelSU 内核模块（LKM 或 GKI）
2. `ksud` 在 late-load 阶段根据运行内核 KMI 选择 `userspace/ksud/bin/aarch64/` 下的预编译 ko
3. `libksud.so` 随 Manager 打包，负责安装、模块管理、日志与 supercall
4. Manager 通过 supercall 与内核通信，提供 UI

## KMI 选择

`ksud` 优先从 `uname -r` 解析 KMI；4.x 内核没有 GKI 标签，按内核版本回退：
`4.19 → android10-4.19`、`4.14 → android9-4.14`、`4.9 → android8-4.9`、`4.4 → android7-4.4`。
Manager 端由 `KmiMatcher` 维护同一份映射。
