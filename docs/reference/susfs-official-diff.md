# SUSFS 官方原版对比与代码择优

## 1. 对比对象

| 项 | 官方原版 | 本项目 |
|---|---|---|
| 仓库 | `gitlab.com/simonpunk/susfs4ksu` | `kernel/susfs/`（LKM 移植） |
| 分支/版本 | `gki-android16-6.12`、`kernel-4.19`、修复分支 `59-f_flags-left-zeroed-on-the-sus_kstat-statfs-path` | `SUSFS_VERSION_STR "v2.3.0"` |
| 形态 | 内核树内补丁（`kernel_patches/`） | 可加载内核模块（LKM） |
| 基线版本 | **v2.3.0** | **v2.3.0** |

结论：两边功能基线一致（v2.3.0），差异来自**实现形态**（树内 vs LKM）以及本项目为 4.x/LKM 做的兼容与扩展。

## 2. 结构性差异

| 维度 | 官方原版 | 本项目 LKM | 择优结论 |
|---|---|---|---|
| 部署方式 | 编译进内核（patch） | `insmod` / late-load | 保持 LKM（临时 root 定位） |
| 挂载隐藏 | 直接改 `fs/namespace.c`、`mnt_id_ida` | kprobe / syscall hook 模拟 | 保持 LKM 实现 |
| 路径隐藏 | `inode->i_mapping->flags` | LSM hook（sb_statfs/inode_permission 等）+ inode 缓存 | 保持（4.x 可用） |
| statfs 伪装 | `vfs_statfs()` 树内修改 | open-redirect 用 `vfs_statfs()` 快照；kstat 未提供 statfs 面 | 见 P1 |
| BTF 依赖 | 无（树内可编译） | BTF 探测，4.x 无 BTF 时降级 | 保持现状 |
| 4.x 支持 | 有 `kernel-4.19/4.14/4.9` 分支 | 4.x CI 交叉编译 + 兼容层 | 本项目独有优势 |
| 额外能力 | — | create gate、open redirect、imports guard 等 LKM 特性 | 保持 |

## 3. 命令 ABI 对比

对比官方 `include/linux/susfs_def.h` 与我们的 `kernel/susfs/kernel/susfs_abi.h`：

- 官方命令 24 个，本项目 23 个
- **仅官方独有**：`CMD_SUSFS_SET_PRIORITY_UID_FILTER (0x60030)`
- 其余命令名称、含义一致

### 优先级：P0 —— 移植 `SET_PRIORITY_UID_FILTER`（已实现，2026-11）

官方 v2.3.0 新增能力：按 app 名单过滤 `getpriority()` 返回值，避免优先级探测暴露目标进程。

官方实现要点（`gki-android16-6.12` 分支）：

1. 命令 `0x60030`，结构 `struct st_susfs_priority_uid_filter { char app_strings[65535]; int err; }`
2. `susfs_parse_priority_app_strings()` 在工作线程解析 app 名 → uid（`packages.list`/PackageManager 数据）
3. `susfs_resolve_priority_app_uid()` / `susfs_is_priority_uid_filtered(uid_t)`
4. 钩子点：
   - `sys_getpriority()`：`current_uid().val >= 10000` 时按过滤表返回
   - `proc_pid_status`/相关 cred 路径：对目标 `cred->uid` 过滤

LKM 实现（已完成，见 `kernel/susfs/kernel/susfs_priority.c`）：

- `susfs_abi.h`：补命令常量与结构体
- `susfs_supercall.c`：注册命令，保存 app_strings，触发解析 worker（kernel thread）
- `kernel/feature/` 或 `susfs/` 新增 `susfs_priority.c`：bitset + uid 解析（复用 KernelSU 的包管理数据）
- syscall hook：在现有 `kernel/hook/` 框架中 hook `getpriority`（`__NR_getpriority`），按过滤表返回 20
- ksud / Manager：新增设置项与命令行入口（调用 supercall）

### 优先级：P1 —— SUS_KSTAT 的 statfs 伪装面（含 f_flags 修复）

官方修复分支 `59-f_flags-left-zeroed-on-the-sus_kstat-statfs-path`：

- 问题：`statfs_by_dentry()` 返回的 `kstatfs.f_flags` 为 0，`sus_kstat` statfs 伪装会暴露
- 修复：额外调用 `calculate_f_flags(no_sus_vfsmnt)` 补齐 `f_flags`

本项目现状：

- LKM **没有** `SUS_KSTAT -> statfs` 伪装面（kstat 只覆盖 getattr/statx/statfs 探测的隐藏路径）
- open-redirect 的 statfs 快照使用 `vfs_statfs()`，内核已正确填 `f_flags`，**不受该 bug 影响**
- 因此该修复**当前不适用**；若未来对齐官方增加 kstat statfs 面，必须同时移植 `calculate_f_flags` 逻辑

### 优先级：P2 —— `clone_mnt()` 竞态修复

官方另一修复：`SUS_MOUNT` 的 `clone_mnt()` 在 static key 关闭 + ksu 进程 unshare 时可能漏置
`VFSMOUNT_MNT_FLAGS_KSU_UNSHARED_MNT`，导致 `mnt_id_ida` 释放不存在的 id；修复为回退相关改动。

本项目现状：

- LKM 不修改 `fs/namespace.c` / `clone_mnt()`，没有该竞态路径 → **不适用**
- 我们的 mount 隐藏通过 kprobe/syscall 层实现，需单独审计（无此标志位逻辑）

## 4. 刻意保留的差异（不择优合并）

| 差异 | 本项目做法 | 理由 |
|---|---|---|
| FUSE 路径隐藏 | 不做 `get_fuse_inode()` 双 mapping 标记 | LSM/inode 层判断与对象绑定，FUSE_BPF 透传下更安全；详见 `sus_path.c` 注释 |
| LSM 注入 | 自研 `lsm_hook.c` + 4.x `lsm_hook_old.c` | 树内补丁不可用；这是 LKM 唯一可行路径 |
| BTF 依赖 | 有 BTF 时用，4.x stub | 4.x 无 BTF，必须降级 |
| statfs 伪装 | 仅 open-redirect 面 | 避免额外 hook 面与可检测性 |

## 5. 代码择优结论

1. **保留**：LKM 实现、LSM 注入、open-redirect/create gate、4.x 兼容层（本项目的核心价值）
2. **不合并**：官方两个树内修复（statfs f_flags、clone_mnt race）——架构上不适用；但记录其问题模型，若引入对应 LKM 面时作为验收项
3. **已实现**：
   - P0：`SET_PRIORITY_UID_FILTER` 内核侧（supercall + setpriority/ioprio kprobe，4.19/4.14/4.9/4.4 编译验证）；ksud/Manager 控制入口待接
   - P1：kstat statfs 伪装面（含 f_flags 修复），视需求决定
4. **验收**：每项移植后跑 4.x CI（4.19 起步）+ 真机 `getpriority` 行为验证

## 6. 参考

- 官方 6.12 分支：`kernel_patches/fs/susfs.c`、`include/linux/susfs.h`、`include/linux/susfs_def.h`
- 官方 4.19 分支：`kernel_patches/50_add_susfs_in_kernel-4.19.patch`
- 修复分支：`59-f_flags-left-zeroed-on-the-sus_kstat-statfs-path`
- 本项目：`kernel/susfs/kernel/`（`sus_path.c`、`sus_mount.c`、`susfs_kstat.c`、`susfs_abi.h` 等）
