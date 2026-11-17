
# 构建

## 依赖

- JDK 21
- Android SDK 36 / Build Tools 36.1.0 / NDK 29
- Rust nightly（aarch64-linux-android / x86_64-linux-android target）
- Python 3（脚本）

## GitHub Actions

| 工作流 | 触发 | 产物 |
|---|---|---|
| `Build Manager (debug)` | 手动 / PR | debug APK |
| `Release` | 推送 `v*` tag | 正式签名 APK + GitHub Release |
| `Build 4.x LKM` / `Build LKM` | 手动 | 4.x / 5.x 预编译 KO |
| `CodeQL` / `Lints` / `Quality` | PR | 检查 |

正式版签名需要仓库 Secrets：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## 构建 ksuinit（可选，嵌入 ksud）

Release 会为 arm64/x86_64 构建 `ksuinit` 并放进 `userspace/ksud/bin/<abi>/ksuinit`，
再编译 ksud；本地需要时按 `.github/workflows/ksuinit.yml` 的同一命令构建：

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/29.0.14206865"
cd userspace/ksuinit
source ../../.github/scripts/setup-rust-build.sh aarch64-linux-android 26
BUILTINS="$($CLANG_PATH --print-resource-dir)/lib/linux/libclang_rt.builtins-aarch64-android.a"
export RUSTFLAGS="-C target-feature=+crt-static -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wno-unused-command-line-argument -C link-arg=$BUILTINS"
cargo build --package ksuinit --target=aarch64-linux-android --release
cp target/aarch64-linux-android/release/ksuinit ../ksud/bin/aarch64/ksuinit
```

## 本地构建管理器

```bash
cd manager
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
./gradlew :app:assembleDebug
```

正式签名：

```bash
./gradlew :app:assembleRelease \
  -PKEYSTORE_FILE=/path/release.jks \
  -PKEYSTORE_PASSWORD=*** \
  -PKEY_ALIAS=*** \
  -PKEY_PASSWORD=***
```

## 构建 ksud

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/29.0.14206865"
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
```

## 多 ABI 构建

默认打包 `arm64-v8a` + `x86_64`。为每个 ABI 构建并 staging daemon：

```bash
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cargo +nightly build --target x86_64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
cp userspace/ksud/target/x86_64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/x86_64/libksud.so
```

`NEKOSU_ABIS`（或 `scripts/build-manager.sh` 的 `ABIS`）可选择 ABI；
没有 staged `libksud.so` 的 ABI 不会被打包。

内置 LKM/ksuinit 资产按 ABI 放在 `userspace/ksud/bin/<abi>/`：arm64 放各 KMI 的
`*_kernelsu.ko` 与 `*_susfs_guard_lkm.ko`；x86_64 放 DDK x86_64 构建的
`*_kernelsu.ko`（Release 中 best-effort，可能缺席；构建步骤见
`.github/workflows/release.yml` 的 `lkm-x86_64` job，与 `ddk-lkm.yml` 的 x86
分支相同）。上游 SUSFS LKM 只支持 arm64，KPM 传输层只支持 Android ARM64；
boot image capsule 补丁也只实现 arm64。

### 构建期 registry 配置

模块仓库默认 `https://modules.kernelsu.org/`；可用 `-PNEKOSU_REGISTRY_URL=...`
（或环境变量）指向自建 registry，空值禁用内置源。自建源使用 KernelSU Modules API v1，
release ZIP 可托管在任意 https 地址，但必须提供 size + SHA-256。

## 版本号

`version.properties` 是唯一版本来源：

- `NEKOSU_VERSION_NAME`：必须与发布 tag 一致（校验时忽略前导 `v`）
- `NEKOSU_VERSION_CODE`：**每次发布递增**（更新链路依赖单调递增）
- Gradle 直接读取该文件；文件缺失时才回退 `40000 + 提交数` / `git describe`

因此发布顺序是：先改 `version.properties` → 提交 → 打 tag → CI 构建，
保证 APK 内版本号与 Release 一致。
