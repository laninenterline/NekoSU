<div align="center">

# NekoSU

**基于 KernelSU 的内核级 root 方案，支持 GKI 5.x 与 4.x 传统内核**

[![Release](https://img.shields.io/github/v/release/laninenterline/NekoSU?label=release)](https://github.com/laninenterline/NekoSU/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/laninenterline/NekoSU/release.yml?label=build)](https://github.com/laninenterline/NekoSU/actions)
[![License](https://img.shields.io/github/license/laninenterline/NekoSU)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84)](https://developer.android.com)

</div>

## 简介

NekoSU 是 KernelSU 的分支版本，包名 `com.nekosu`，提供内核模块 + 用户态守护进程 +
Android 管理器的完整 root 方案。支持 GKI 内核（5.4 – 6.18）以及 4.19 / 4.14 / 4.9 / 4.4
传统内核（实验性）。

## 特性

- **KernelSU 兼容**：继承 KernelSU 的 supercall、模块、App Profile、SELinux 处理
- **SUSFS**：内置 SUSFS 支持，5.x 与 4.x 均提供 LKM（4.x 由 CI 交叉编译）
- **多管理器**：可与其他 KernelSU 分支管理器共存并动态合并
- **统一日志中心**：SU / Magica / crash / metamodule / problem 日志聚合，支持搜索、导出、清除
- **包管理器观察**：跟踪前台应用、应用图标网格布局、多种管理器图标选择
- **设置搜索**：全量设置项可搜索
- **4.x 支持**：4.19 / 4.14 / 4.9 / 4.4 预编译 LKM（实验性，见 [内核支持](docs/KERNELS.md)）

## 安装

1. 从 [Releases](https://github.com/laninenterline/NekoSU/releases) 下载 APK
2. 按设备内核选择安装方式：GKI 设备刷入 boot 镜像；4.x 设备通过 `ksud` late-load
3. 首次启动按管理器引导完成安装

> 需要 Android 8.0+；4.4 内核（Android 7）设备无法安装管理器，仅支持 ksud 手动模式。

## 构建

见 [构建指南](docs/BUILDING.md)。快速开始：

```bash
# ksud
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml

# Manager
cd manager && ./gradlew :app:assembleDebug
```

## 仓库结构

```text
kernel/            内核模块源码
kernel/susfs/      SUSFS LKM（vendored）
userspace/ksud/    Rust 守护进程（内嵌各 KMI 预编译 ko）
userspace/ksuinit/ ksuinit
manager/           Android 管理器
uapi/              共享 UAPI 头
scripts/           构建脚本
tools/             APK 重打包等工具
bindings/js/       JavaScript 绑定
docs/              文档（架构 / 构建 / 内核 / 发布）
```

## 文档

- [架构说明](docs/ARCHITECTURE.md)
- [构建指南](docs/BUILDING.md)
- [内核支持](docs/KERNELS.md)
- [发布流程](docs/RELEASING.md)

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU)
- [SUSFS4KSU](https://github.com/simonpunk/susfs4ksu)
- 所有上游贡献者

## 许可证

[GPL-2.0](LICENSE)
