
# 内核与 KMI

## 支持矩阵

| 内核 | KMI | 形态 |
|---|---|---|
| 4.4 | android7-4.4 | 预编译 KO |
| 4.9 | android8-4.9 | 预编译 KO |
| 4.14 | android9-4.14 | 预编译 KO |
| 4.19 | android10-4.19 | 预编译 KO |
| 5.4 | android11-5.4 | 需自定义 KO |
| 5.10 | android12-5.10 | GKI LKM |
| 5.15 | android13-5.15 | GKI LKM |
| 6.1 | android14-6.1 | GKI LKM |
| 6.6 | android15-6.6 | GKI LKM |
| 6.12 | android16-6.12 | GKI LKM |

## KO 来源

- GKI：CI 构建对应 KMI 的 `kernelsu.ko` / `susfs_guard_lkm.ko`，打包进 APK assets
- 4.x：仓库内 `userspace/ksud/bin/aarch64/` 提供预编译产物
- 5.4：没有统一 GKI 镜像，需用户导入匹配内核的 KO 到
  `/data/data/com.nekosu/files/lkm/custom/kernelsu.ko`

## KMI 匹配

`ksud` 启动时读取内核版本（`uname -r`），推断 KMI，优先加载用户自定义 KO，
然后回退到内置 assets。不匹配时首页会提示并在 KMI 诊断页给出建议。

## SUSFS

- SUSFS 以独立 LKM 加载（`susfs_guard_lkm.ko`），加载参数 `expose_proc=1`
- 若内核已内置 SUSFS，则跳过加载
- 支持隐藏路径、kstat、进程、maps 与 su 日志
- SUSFS 集成开关（`ksud susfs status|enable|disable`）按需加载 LKM 并持久化开机
  自动加载；内核已内置 SUSFS 时（builtin）跳过加载

## 多 ABI 说明

- arm64-v8a：各 KMI 的 `kernelsu.ko` 与 `susfs_guard_lkm.ko` 打包进 APK assets，
  LKM/SUSFS/KPM 完整可用
- x86_64：Release 会用 DDK x86_64 构建 `kernelsu.ko`（best-effort），并构建
  `ksuinit` 嵌入 ksud，可走 `ksud late-load`；上游 SUSFS LKM 只有 arm64 版本，
  因此 x86_64 没有 SUSFS LKM，boot image capsule 补丁与 KPM 传输层也仅 arm64
- riscv64：仅产出 ksud artifact，不打包管理器
