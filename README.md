# NekoSU

NekoSU 是一个基于 KernelSU 的 Android Root 管理器分支，**只支持临时 root（LKM / 漏洞临时加载）模式**：
不刷 boot、不安装/卸载内核，重启后 root 失效，需要重新通过临时加载手段获得。

## 特性

- su 授权、超级用户管理、模块安装/刷写（保留 KernelSU 模块体系）
- **内嵌内核模块**：`android14-6.1` / `android15-6.6` / `android16-6.12` / `android17-6.18` 的 `kernelsu.ko`（官方 GKI 镜像编译）随 `ksud` 打进 APK；
  ksud 会按运行内核 release 自动选择对应 KMI 的 ko。SUSFS LKM 覆盖 6.1/6.6/6.12/6.18。
- **内嵌 SUSFS LKM**：`android15-6.6_susfs_guard_lkm.ko`（[inforcqb/susfs4ksu-lkm](https://github.com/inforcqb/susfs4ksu-lkm) v2.3.0），
  `late-load` 加载完 kernelsu.ko 后自动加载（`expose_proc=1`），无需单独刷模块
- `ksud late-load` / Jailbreak 路径；模块刷写、SuSFS 设置界面

## 不支持的（有意移除）

- 刷写 boot / 分区、安装内核、补丁 boot（GKI 内置模式）
- 永久卸载 / 恢复原厂镜像
- 内核 zip 安装（Intent 会提示仅支持临时 root）

## 目录结构

```
manager/          Android 管理器（Kotlin + Compose）
userspace/ksud/   Rust 用户态守护进程（含内嵌 ko 资源，构建产物不入库）
kernel/           KernelSU 内核模块源码（GPL-2.0）
uapi/             内核/用户态共享接口定义
scripts/          构建脚本（含 scripts/build_kmi.sh：按 KMI 构建 kernelsu.ko + SUSFS ko）
```

## 构建环境

| 组件 | 版本/路径 |
|---|---|
| Gradle | 9.7.0（wrapper） |
| AGP | 9.4.0 |
| Kotlin | 2.4.20 |
| compileSdk / targetSdk | 37 |
| buildTools | 36.1.0 |
| CMake | 4.1.0（`manager/app/build.gradle.kts` 固定） |
| NDK | r29（`/root/android-ndk-r29`） |
| Rust | nightly（`cargo +nightly`） |
| 内核编译 clang | clang-18（如 `/usr/lib/llvm-18/bin`） |
| DDK KDIR | `android15-6.6`（GKI `android15-6.6.89_r00`，4K 页） |

本机路径配置（不入库）：
- `manager/local.properties`: `sdk.dir=/root/android-sdk`
- `~/.gradle/gradle.properties`: `android.aapt2FromMavenOverride=/root/android-sdk/build-tools/36.1.0/aapt2`
- 内核 ko 构建需要 DDK 本地模式目录：`/opt/ddk/kdir/<kmi>` + `/opt/ddk/clang/<ver>`

## 构建

```bash
# 0) （可选）从 GKI 源码生成 KDIR：android15-6.6.89_r00
scripts/build-kdir.sh

# 1) 构建 kernelsu.ko（需要 KSU_EXPECTED_SIZE/HASH，见下节）
KSU_EXPECTED_SIZE=744 \
KSU_EXPECTED_HASH=7897a4ecab0ed3e82f03bf8d5e3238b254cebce285385099bd29151b3aaf327c \
scripts/build-lkm.sh

# 2) 构建 SUSFS LKM（首次会自动 clone 上游项目）
scripts/build-susfs-lkm.sh

# 3) 构建 APK（编译 ksud 并内嵌 ko，然后 gradle）
# 注意：bin/*.ko 变化后必须 cargo clean -p ksud（rust-embed 会缓存资源）
# 可选：让 ko vermagic 与设备完全一致（推荐，部分 OEM 内核 kmsg 不打印 required vermagic）
# DEVICE_RELEASE=$(adb shell getprop ro.kernel.version)  # 或直接用 adb shell uname -r 的值
scripts/build-manager.sh
```

产物：`manager/app/build/outputs/apk/debug/NekoSU_<versionName>_<versionCode>-debug.apk`

## 管理器签名与内核信任

内核通过 APK 的 **v2 签名证书 DER** 的 `size + sha256` 识别 manager（`kernel/manager/apk_sign.c`）。
编译 LKM 时必须把同一组值写入 `${KSU_EXPECTED_SIZE}` / `${KSU_EXPECTED_HASH}`：

```bash
# 从 keystore 读取（脚本：scripts/get-cert-size-hash.sh）
keytool -exportcert -alias androiddebugkey \
  -keystore ~/.android/debug.keystore -storepass android -keypass android -file cert.der
stat -c%s cert.der
sha256sum cert.der | cut -d' ' -f1
```

当前仓库开发用的 debug 证书值：`size=744`、`hash=7897a4ecab0ed3e82f03bf8d5e3238b254cebce285385099bd29151b3aaf327c`。
发布版本请使用自己的 release keystore，重新计算并重编 LKM，否则内核不会信任该 APK。

## SUSFS（LKM）

SUSFS 以可加载模块形式提供，来源 [inforcqb/susfs4ksu-lkm](https://github.com/inforcqb/susfs4ksu-lkm)（GPL-2.0），
构建时编入 ksud 资源，`ksud late-load` 后在 `/proc/susfs_path` 不存在时自动加载。
加载成功后会注册 SUSFS supercall 与 `/proc/susfs_*` 节点，管理器 SuSFS 页面可读取版本并下发规则。
若内核已内置 SUSFS，则自动跳过。

## 许可与致谢

- 本项目基于 [BakaSU](https://github.com/Baka-SU/BakaSU)（KernelSU 分支）整理，遵循 GPL-3.0（见 `LICENSE`）
- KernelSU 内核模块、uapi、ksud 源自 [KernelSU](https://github.com/tiann/KernelSU)（GPL-2.0）
- SUSFS LKM 源自 [inforcqb/susfs4ksu-lkm](https://github.com/inforcqb/susfs4ksu-lkm)（GPL-2.0），
  其功能逻辑来自 [simonpunk/susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu)
- 原 BakaSU 美术资源已移除，当前图标为本项目素材

## 已知限制

- 仅 arm64-v8a
- 仅临时 root：每次重启后需要重新触发加载（Jailbreak / 漏洞工具）
- 内核符号通过 `/proc/kallsyms` 绑定，需 root 上下文；设备内核缺少符号时会加载失败
- 仅支持 GKI 2.0（5.10+）内核

## 管理器签名证书限制

内核 `kernel/manager/apk_sign.c` 限制管理器 APK 的 v2 证书 DER 不超过 **1024 字节**
(`CERT_MAX_LENGTH`)，且 APK 必须只有 v2 签名、不能带 v1。
生成 release keystore 时请用 RSA 2048 或 EC P-256（EC 约 318 字节）；
RSA 4096 的证书约 1.3KB，会被内核以 `cert length overlimit` 拒绝。


## 多 KMI 构建（GKI 6.x 全支持）

```bash
# 需要：git-lfs + zstd；6.12+ 还需宿主 libdw-dev / libdwarf-dev；
#       DDK 官方预编译镜像仓库（默认 /root/ddk-prebuilts）
for kmi in android14-6.1 android15-6.6 android16-6.12 android17-6.18; do
  KSU_EXPECTED_SIZE=<证书DER大小> KSU_EXPECTED_HASH=<证书sha256> \
    scripts/build_kmi.sh "$kmi"
done
# 然后重编 ksud（必须，rust-embed 内嵌新 ko）：
scripts/build-manager.sh
```

脚本会自动处理：

- 按 KMI 拉取官方 `kdir-min` + `src` 镜像（git-lfs），解压到 `/opt/ddk/{kdir,src}/<kmi>`
- 清掉官方镜像里的 x86_64 宿主工具，用本机 clang 重建 fixdep/modpost
- ≤6.11 走旧 Kbuild（`M=输出 src=源码`）；6.12+ 走新 Kbuild（`M=源码 MO=输出`）并自动 `KSU_SKIP_PREINIT=1`
- 6.12 的 modpost 空 `__version_ext_names` 数组问题自动修补
- 关闭模块 BTF（避免 resolve_btfids 宿主依赖）
- 编译 `kernelsu.ko` 与 `susfs_guard_lkm.ko`，strip 后放入 `userspace/ksud/bin/aarch64/`
- 清理临时 KDIR/SRC（`KEEP=1` 可保留）

已知限制：6.18 的 SUSFS 源码可能需要额外清理 `modules.order` 等旧构建残留（脚本已自动处理）。

