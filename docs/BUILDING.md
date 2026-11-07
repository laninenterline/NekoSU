# 构建指南

## 前置

- JDK 21、Android SDK（build-tools 36.1.0）
- Rust nightly + `cargo-ndk` / NDK r29（ksud 交叉编译）
- LLVM/Clang、flex、bison、aarch64 交叉 gcc（内核 LKM）

## ksud

```bash
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
```

## Manager

```bash
cd manager
./gradlew :app:assembleDebug
```

## 内核 LKM（5.x GKI）

由 `.github/workflows/build-lkm.yml` 在 CI 中针对 DDK 镜像构建；本地可用
`scripts/build_kmi.sh`，按脚本头部参数传入 KDIR、KMI、OUT 等。

## 内核 LKM（4.x）

由 `.github/workflows/build-4x-lkm.yml` 交叉编译四个分支：
`deprecated/android-4.19`、`-4.14`、`-4.9`、`-4.4`，
产物为 `kernelsu.ko` + `susfs_guard_lkm.ko`，提交进 `userspace/ksud/bin/aarch64/`。
4.x 无 GKI/DDK，只能预编译入库。
