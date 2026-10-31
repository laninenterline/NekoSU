# Kernel 与 KernelSU 上游同步分析

- 上游仓库：https://github.com/tiann/KernelSU （比对 commit `b1fce49`）
- 本地：`kernel/`（保留 SUSFS、多 KMI、多管理器、临时 root 等增量）

## 总览

| 类别 | 数量 |
|---|---|
| 仅上游有（我们缺失） | 11 |
| 仅我们有（增量/补丁） | 119 |
| 双方都有但不同 | 82 |
| 其中：含自定义标记（保留审查） | 25 |
| 其中：无自定义标记（上游替换候选） | 57 |

## 一、仅上游有的文件（评估是否引入）

- `hook/lsm_hook.c`
- `hook/lsm_hook.h`
- `include/util.h`
- `preinit/runner/nolibc/README.md`
- `preinit/runner/nolibc/arch-loongarch.h`
- `preinit/runner/nolibc/arch-m68k.h`
- `preinit/runner/nolibc/arch-mips.h`
- `preinit/runner/nolibc/arch-powerpc.h`
- `preinit/runner/nolibc/arch-s390.h`
- `preinit/runner/nolibc/arch-sh.h`
- `preinit/runner/nolibc/arch-sparc.h`

## 二、无自定义标记的差异文件（优先逐文件对比上游）

| 差异行数 | 文件 |
|---|---|
| 464 | `selinux/sepolicy.c` |
| 356 | `manager/throne_tracker.c` |
| 281 | `hook/arm64/syscall_hook.c` |
| 213 | `hook/arm64/patch_memory.c` |
| 195 | `policy/allowlist.c` |
| 129 | `feature/adb_root.c` |
| 116 | `infra/file_wrapper.c` |
| 102 | `policy/app_profile.c` |
| 90 | `preinit/runner/runner.c` |
| 88 | `include/arch.h` |
| 75 | `hook/syscall_event_bridge.c` |
| 70 | `manager/manager_identity.h` |
| 61 | `hook/x86_64/patch_memory.c` |
| 56 | `hook/x86_64/syscall_hook.c` |
| 56 | `hook/syscall_hook_manager.c` |
| 56 | `hook/riscv64/syscall_hook.c` |
| 52 | `infra/su_mount_ns.c` |
| 42 | `preinit/runner/nolibc/sys/stat.h` |
| 42 | `hook/syscall_hook.h` |
| 33 | `runtime/boot_event.c` |
| 28 | `feature/shell_su_adb_only.c` |
| 24 | `manager/throne_tracker.h` |
| 21 | `sulog/event.h` |
| 14 | `preinit/runner/nolibc/sys/timerfd.h` |
| 14 | `feature/selinux_hide.h` |
| 12 | `feature/module_blacklist.h` |
| 10 | `supercall/perm.c` |
| 10 | `runtime/ksud.h` |
| 10 | `policy/feature.c` |
| 10 | `manager/pkg_observer.c` |
| 10 | `include/klog.h` |
| 9 | `manager/apk_sign.h` |
| 9 | `infra/su_mount_ns.h` |
| 9 | `infra/event_queue.c` |
| 9 | `hook/tp_marker.c` |
| 9 | `hook/setuid_hook.h` |
| 9 | `feature/adb_root.h` |
| 8 | `supercall/internal.h` |
| 8 | `infra/seccomp_cache.h` |
| 8 | `hook/patch_memory.h` |
| 7 | `policy/allowlist.h` |
| 5 | `selinux/sepolicy.h` |
| 5 | `preinit/runner/nolibc/types.h` |
| 5 | `preinit/runner/nolibc/time.h` |
| 5 | `policy/app_profile.h` |
| 4 | `sulog/fd.c` |
| 4 | `preinit/runner/nolibc/sys/random.h` |
| 4 | `preinit/runner/nolibc/sys.h` |
| 4 | `manager/manager_observer.h` |
| 4 | `infra/seccomp_cache.c` |
| 3 | `feature/sulog.c` |
| 2 | `preinit/runner/nolibc/sys/mount.h` |
| 2 | `preinit/runner/nolibc/stackprotector.h` |
| 2 | `policy/feature.h` |
| 2 | `infra/event_queue.h` |
| 2 | `feature/kernel_umount.h` |
| 2 | `LICENSE` |

## 三、含自定义标记的差异文件（保留，等上游相关重构后再对齐）

- `Kbuild`
- `Kconfig`
- `Makefile`
- `build-all-x64.sh`
- `build-all.sh`
- `core/init.c`
- `feature/kernel_umount.c`
- `feature/module_blacklist.c`
- `feature/selinux_hide.c`
- `feature/sucompat.c`
- `feature/sucompat.h`
- `hook/setuid_hook.c`
- `include/ksu.h`
- `infra/symbol_resolver.c`
- `manager/apk_sign.c`
- `preinit/preinit.c`
- `preinit/preinit.h`
- `runtime/ksud_integration.c`
- `selinux/rules.c`
- `selinux/selinux.c`
- `selinux/selinux.h`
- `setup.sh`
- `sulog/event.c`
- `supercall/dispatch.c`
- `supercall/supercall.c`

## 四、仅我们有的文件（我们的增量，按目录）

| 目录 | 文件数 |
|---|---|
| `feature` | 18 |
| `hook` | 14 |
| `.` | 11 |
| `infra` | 10 |
| `manager` | 10 |
| `compat` | 9 |
| `selinux` | 7 |
| `policy` | 6 |
| `supercall` | 6 |
| `tools` | 6 |
| `hook/arm64` | 4 |
| `runtime` | 4 |
| `sulog` | 4 |
| `tools/code_generation/syscall_no` | 4 |
| `core` | 2 |
| `compat/backport` | 1 |
| `hook/arm32` | 1 |
| `tools/code_generation` | 1 |
| `tools/code_generation/preinit_runner` | 1 |

## 五、建议合并顺序

1. **独立小文件**：先处理无标记差异里 `diff` 行数少、且不与 headers/调用方联动的文件
2. **上游新增文件**：逐个评估是否是我们需要的修复/新能力，避免引入与我们 SUSFS/多 KMI 冲突的接线
3. **带标记文件**（Kbuild/Kconfig/Makefile/sucompat/preinit/symbol_resolver 等）：保持现状，等上游对应重构完成后整体对齐
4. 每批替换后必须重新编译 6.6 与 6.12 LKM（本地或 CI），确认 `__versions`/符号绑定/加载正常再继续

> 说明：BakaSU 的实现并不都是“旧代码”，其中 SUSFS、多 KMI、多管理器、preinit 等是我们刻意保留的优秀增量；
> 本清单的原则是：**无我们逻辑的文件优先吸收上游；有我们逻辑的文件只做上游修复的手工移植**。

## 六、执行进度

- [x] `hook/tp_marker.c` 改用上游版本（移除高频路径 noisy logs）
- [x] `infra/seccomp_cache.[ch]` 改用上游实现（统一 5.10+ 守卫）
- [x] 构建脚本修复 6.12 modpost 空 `__version_ext_names`（输出 `= ""`）
- [x] `android16-6.12` 的 kernelsu.ko + SUSFS ko 编译验证通过
- [ ] `feature/adb_root.*`、`hook/arm64/syscall_hook.c`、`manager/throne_tracker.*` 等需连同调用方一起移植上游重构版
- [x] `kernel/tools/code_generation/syscall_no/syscall_no.mk`：旧式内核（≤6.10）按源码树 `unistd32.h` 传入 compat 表，修复 `asm/unistd_compat_32.h` 缺失
- [x] `scripts/build_kmi.sh`：构建前清理内核树里旧的 `compat/syscall_no.h` / `kernelsu.mod.c`
- [x] `android14-6.1`（旧式 Kbuild）编译验证通过
