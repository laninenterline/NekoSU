# 内核支持

> 本项目**只提供临时 root（越狱模式）**：所有 ko 均通过用户态 late-load 加载，
> 不刷写 boot 镜像，重启后失效。

## 1. 支持矩阵

| 内核系列 | KMI | kernelsu.ko | SUSFS LKM | 状态 |
|---|---|---|---|---|
| 4.19 | android10-4.19 | ✅ | ✅ | 实验性 |
| 4.14 | android9-4.14 | ✅ | ✅ | 实验性 |
| 4.9 | android8-4.9 | ✅ | ✅ | 实验性 |
| 4.4 | android7-4.4 | ✅ | ✅ | 实验性 |
| 5.4 | android11-5.4 | ✅ | ✅ | 稳定 |
| 5.10 | android12-5.10 / android13-5.10 | ✅ | ✅ | 稳定 |
| 5.15 | android13-5.15 / android14-5.15 | ✅ | ✅ | 稳定 |
| 6.1 | android14-6.1 | ✅ | ✅ | 稳定 |
| 6.6 | android15-6.6 | ✅ | ✅ | 稳定 |
| 6.12 | android16-6.12 | ✅ | ✅ | 稳定 |
| 6.18 | android17-6.18 | ✅ | ✅ | 稳定 |

## 2. KMI 识别

```text
uname -r  ──有 androidNN-x.y?──> 使用该 KMI
    │否
    ▼
/vendor/lib/modules/*.ko 的 vermagic
    │否
    ▼
4.19→android10-4.19  4.14→android9-4.14
4.9 →android8-4.9    4.4 →android7-4.4
```

Manager 端 `KmiMatcher` 维护相同映射；`ksud` 的 `--kmi` 可强制覆盖。

## 3. 4.x 移植说明

4.x 没有 GKI/DDK，ko 由 CI 交叉编译后提交到 `userspace/ksud/bin/aarch64/`。
主要兼容层（按出错顺序）：

| 类别 | 处理 |
|------|------|
| 旧 Kbuild | 预建 `$OUT` 模块目录、复制 `kernel/Makefile` 供 modpost |
| dtc/flex | `HOSTCFLAGS=-fcommon`、per-object `-fcommon`、lexer 输入修正 |
| 交叉工具链 | `KMI_MAKE_TC` 使用 `aarch64-linux-gnu-*`，避免 host clang 的寄存器错误 |
| SELinux | 手动生成 `flask.h`；`CONFIG_SECURITY_SELINUX_SIDTAB_HASH_BITS` 用 KCFLAGS 提供 |
| sched 头 | `<4.11` 用 `linux/sched.h` 替代 `sched/signal.h`、`sched/mm.h`、`sched/task.h` |
| fsnotify | 4.20 / 4.18 / 4.10 三个签名分支（mark、iter_info 差异） |
| 内核 API | `static_assert`、`regs_set_return_value`、`strscpy_pad`、`MODULE_IMPORT_NS`、`syscall_get_arguments`、`regs_get_kernel_argument`、`p4d→pgd`、`kvmalloc(_array)`、`memalloc_nofs`、`__nocfi` |
| statx | 4.11+ 才存在：4.x guard 掉 kstat 的 statx 实现，并提供 `__NR_statx` 占位 |
| BTF | 4.x 无 BTF：`btf_layout.c` stub，SELinux sid / fsnotify 生命周期特性降级 |
| LSM | 4.14/4.9/4.4 的 `security_hook_list` 是 `list_head`：`lsm_hook_old.c` 独立实现；4.19 及 5.x 用 hlist 实现 |

## 4. 限制

- 4.x 为实验性，**未在真机验证**；遇到问题请附 `dmesg`、内核版本、KMI
- 4.x 的 BTF 依赖特性关闭（SELinux sid 重标记、fsnotify 生命周期）
- 4.4 设备通常是 Android 7（SDK 24/25），Manager minSdk=26 无法安装，只能 `ksud` 手动 late-load
- 4.19 的 LSM 走 hlist 实现，与 5.x 一致

## 5. 新增 KMI 检查清单

1. 在 `scripts/build_kmi.sh` 或 4.x workflow 中加入源码分支与 defconfig
2. 构建 `kernelsu.ko` + `susfs_guard_lkm.ko`，检查 vermagic
3. 提交 ko 到 `userspace/ksud/bin/aarch64/` 并更新 `.gitignore` 例外
4. 更新 `KmiMatcher.kt`、`ksud` 的 KMI 回退逻辑
5. 更新本页与 `CHANGELOG.md`
