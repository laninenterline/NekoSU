# Changelog

## v1.0.1 — 第二个正式发布版

### 内核
- 新增 4.19 / 4.14 / 4.9 / 4.4 传统内核支持（实验性）
- 4.x 新增 CI 交叉编译流水线，产出 `kernelsu.ko` + `susfs_guard_lkm.ko`
- 4.x 实现 `struct list_head` 版 LSM hook 注入（4.14 / 4.9 / 4.4）
- 大量 4.x 兼容层：旧 Kbuild/dtc、sched 头、fsnotify、statx、kvmalloc 等

### Manager
- 支持 4.x KMI 选择与显示
- 设置搜索、统一日志中心、应用图标设置、多管理器合并等改进
- 发布构建不包含开发者调试入口

### 其它
- 文档与目录结构重构
- 仅保留正式版本历史

## v1.0.0 — 第一个正式发布版

- 基于 KernelSU 的首次正式发布
- GKI 5.4 – 6.18 内核支持
