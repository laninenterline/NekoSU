
# SUSFS 官方原版对比

## 对比对象

| 项 | 官方原版 | NekoSU |
|---|---|---|
| 仓库 | `gitlab.com/simonpunk/susfs4ksu` | `kernel/susfs/` |
| 版本 | v2.3.0 | v2.3.0 |
| 形态 | 内核树内补丁 | 可加载内核模块（LKM） |

## 结论

功能基线一致（v2.3.0）。差异集中在实现形态：

- NekoSU 将 SUSFS 改写为独立 LKM，按 KMI 编译并随管理器分发
- 为 4.x 内核补充兼容层（LSM hook、kvmalloc、memalloc_nofs、p4d 等）
- 增加 LKM 场景下的初始化与参数传递（`expose_proc=1`）

## 已对齐的官方特性

- 路径 / kstat / 进程 / maps 隐藏
- su 日志控制
- 优先级 UID 过滤（`SET_PRIORITY_UID_FILTER`）
- 版本与特性查询 supercall

## 同步策略

官方发布新版本时，先对比 `kernel/susfs` 与上游分支差异，再按功能优先级移植；
移植后需在 4.x 与 GKI 两条构建链上验证。
