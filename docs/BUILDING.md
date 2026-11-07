# 构建指南

## 1. 环境要求

| 组件 | 版本/说明 |
|------|-----------|
| JDK | 21 |
| Android SDK | build-tools 36.1.0、CMake 4.1.0 |
| Rust | nightly（ksud） |
| Android NDK | r29（交叉编译 ksud） |
| LLVM/Clang | 18+（LKM 构建） |
| 交叉 gcc | `aarch64-linux-gnu-gcc`（4.x LKM 部分阶段） |
| 其它 | flex、bison、git、curl |

## 2. ksud（Rust）

```bash
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
```

交叉编译环境（本地）：

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
TC=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64
export CC_aarch64_linux_android=$TC/bin/aarch64-linux-android29-clang
export CXX_aarch64_linux_android=$TC/bin/aarch64-linux-android29-clang++
export AR_aarch64_linux_android=$TC/bin/llvm-ar
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$TC/bin/aarch64-linux-android29-clang
export BINDGEN_EXTRA_CLANG_ARGS="--target=aarch64-linux-android29 --sysroot=$TC/sysroot"
```

产物复制到 Manager：

```bash
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
```

## 3. Manager（Android）

Debug：

```bash
cd manager
./gradlew :app:assembleDebug
```

Release（本地需签名配置）：

```bash
cd manager
./gradlew :app:assembleRelease -PRELEASE_KEYSTORE=... -PRELEASE_KEY_ALIAS=... \
  -PRELEASE_KEYSTORE_PASSWORD=... -PRELEASE_KEY_PASSWORD=...
```

CI 使用 GitHub Secrets（见 [RELEASING](RELEASING.md)）。发布构建不包含开发者调试入口。

## 4. 内核 LKM（5.x GKI）

由 CI `.github/workflows/build-lkm.yml` 使用 DDK 镜像构建，产物为
`<KMI>_kernelsu.ko` 与 `<KMI>_susfs_guard_lkm.ko`。

本地构建入口：`scripts/build_kmi.sh`，需要准备内核源码/KMI 参数：

```bash
KDIR=/path/to/kernel SRC=/path/to/kernel OUT=out/kmi-android12-5.10 \
  KMI=android12-5.10 ARCH=arm64 ./scripts/build_kmi.sh
```

## 5. 内核 LKM（4.x）

4.x 无 GKI/DDK，由 `.github/workflows/build-4x-lkm.yml` 矩阵交叉编译：

| KMI | 源码分支 | defconfig |
|---|---|---|
| android10-4.19 | `deprecated/android-4.19-gki-dev` | `gki_defconfig` |
| android9-4.14 | `deprecated/android-4.14` | `defconfig` |
| android8-4.9 | `deprecated/android-4.9` | `defconfig` |
| android7-4.4 | `deprecated/android-4.4` | `defconfig` |

CI 关键步骤：生成 `flask.h`、旧 Kbuild 修复、`HOSTCFLAGS=-fcommon`、交叉 gcc、
关闭 `CONFIG_WERROR`、SELinux/seccomp 配置、构建 kernelsu 与 SUSFS 两个模块，
最后上传 artifact 并提交回仓库。

## 6. 产物校验

```bash
modinfo userspace/ksud/bin/aarch64/android12-5.10_kernelsu.ko
# 检查 vermagic 与目标内核一致

apksigner verify --print-certs manager/app/build/outputs/apk/release/*.apk
# 发布签名 SHA-256 应为 e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0
```

## 7. 常见构建问题

| 现象 | 处理 |
|------|------|
| `yylloc` 多重定义（dtc） | `HOSTCFLAGS=-fcommon` + per-object `-fcommon` |
| `register 'sp' unsuitable for global register variables` | 使用 aarch64 交叉 gcc 的 `KMI_MAKE_TC` |
| `llvm-strip: No such file` | 脚本回退到 PATH 中的 `llvm-strip`/`strip` |
| 4.9/4.4 `linux/sched/signal.h` 不存在 | 已兼容，改包含 `linux/sched.h` |
| 4.x 无 statx / p4d / BTF | 通过版本 guard 关闭或 stub |
