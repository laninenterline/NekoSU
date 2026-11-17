
# AGENTS.md

本文件面向在本仓库工作的编码代理与新贡献者：说明仓库导航、构建/测试约定、
发布与安全约束。修改行为前先读本文件与 `docs/`。

## 项目定位（硬约束）

NekoSU 是**仅临时 root（越狱模式）**的 Root 管理器：

- 不刷 boot / init_boot，不改内核镜像；root 通过 `ksud late-load` 在运行时获得
- 所有能力必须能在一次越狱 + late-load 后建立，重启即失效
- 不接受在线服务依赖（更新检查除外）、绕过签名校验或通用反检测方案
- 管理器更新必须同签名；隐藏管理器的迁移/恢复必须保持可回滚路径

违反以上约束的改动直接拒绝，详见 `CONTRIBUTING.md`、`docs/ARCHITECTURE.md`。

## 仓库地图

| 路径 | 内容 | 技术栈 |
|---|---|---|
| `manager/` | Android 管理器（UI、ViewModel、Repository） | Kotlin + Compose |
| `ui-shared/` | Android 与浏览器版共享的 platform-neutral Compose 组件 | Kotlin/Compose（commonMain） |
| `webui-cmp/` | 浏览器版 UI（模块 WebUI 之外的独立页面） | Compose Multiplatform（wasmJs） |
| `userspace/ksud/` | ksud 用户态守护进程 | Rust（nightly） |
| `userspace/ksud/src/kpm*.rs` | KPM 后端（命令/存储/ELF/运行时/集成状态，PoC） | Rust |
| `userspace/ksud/src/android/susfs/` | SUSFS 集成与配置命令 | Rust |
| `userspace/ksuinit/` | 早期初始化辅助 | Rust |
| `kernel/` | 内核补丁与 SUSFS 源码 | C |
| `uapi/` | 内核/用户态共享头文件 | C headers |
| `tools/` | 发布校验、源码审计、APK 重打包 | Python |
| `scripts/` | 本地构建辅助 | Bash |
| `docs/` | 用户/开发者文档 | Markdown |
| `manager/app/src/main/java/com/nekosu/data/kpm` | KPM 管理面（协议/导入/记录/入口开关） | Kotlin |
| `manager/app/src/main/java/com/nekosu/data/module/registry` | 模块仓库协议/缓存/完整性 | Kotlin |
| `version.properties` | 版本契约（唯一版本来源，发布时递增 CODE） | properties |
| `.github/workflows/` | CI（构建、Quality、Release、LKM） | GitHub Actions |

## 构建

环境：JDK 21、Android SDK 36/37、Build Tools 36.1.0、NDK 29、Rust nightly
（`aarch64-linux-android` / `x86_64-linux-android`）。详见 `docs/BUILDING.md`。

```bash
# 管理器（先准备 manager/local.properties: sdk.dir=...）
cd manager
./gradlew :app:assembleDebug

# 单元测试 + lint（CI Quality 同款）
./gradlew :app:testDebugUnitTest :app:lintDebug

# ksud（按 ABI）
cargo +nightly build --target aarch64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cargo +nightly build --target x86_64-linux-android --release \
  --manifest-path userspace/ksud/Cargo.toml
cp userspace/ksud/target/aarch64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/arm64-v8a/libksud.so
cp userspace/ksud/target/x86_64-linux-android/release/ksud \
   manager/app/src/main/jniLibs/x86_64/libksud.so

# 一键（ksud + APK）
scripts/build-manager.sh
```

构建产物与内嵌 daemon ABI 由 `NEKOSU_ABIS` / `ABIS` 选择（默认 arm64-v8a + x86_64）。
没有对应 `jniLibs/<abi>/libksud.so` 的 ABI 不会被打包。

## 测试

- 位置：`manager/app/src/test/java/`（JVM 单元测试，JUnit 4）
- 运行：`cd manager && ./gradlew :app:testDebugUnitTest`
- 约定：
  - 不依赖 Android 设备、模拟器或 Robolectric；不调用 Android framework API
  - 只测试纯 JVM 逻辑（`data/system`、`data/susfs`、`ui/webui` 中的纯函数/模型等）
  - 用 `org.junit.rules.TemporaryFolder` 管理临时文件；用 `assertThrows` 断言异常
  - 测试同模块的 `internal` 符号可直接访问（test source set 是 main 的 friend）
  - 新增测试按被测主题放入对应包；类名以 `Test` 结尾
  - 不新增测试依赖；现有依赖为 `junit:junit:4.13.2`，其余实现依赖在测试运行时可用
- CI：`Quality` 会跑 `:app:testDebugUnitTest`，并校验
  1) `app/src/test` 不含 `import android.*`；2) 不存在 `app/src/androidTest`；
  3) Gradle 实际生成 JUnit 结果文件（防止测试被静默移除）
- 涉及 Android 行为（隐藏管理器迁移、WebView 下载落盘、UI 状态、KPM/registry 的
  Android 边界）的部分无法用 JVM 测试覆盖时，在 PR 中说明手动验证方式。

## 发布与版本契约

- `version.properties` 是唯一版本来源；Gradle 直接读取（缺失时才回退旧的
  `40000 + 提交数` / `git describe` 逻辑）
- **每次发布递增 `NEKOSU_VERSION_CODE`**（当前 `40002`）；`NEKOSU_VERSION_NAME`
  必须与发布 tag 一致（校验时忽略前导 `v`）
- 管理器更新与隐藏态更新都依赖 versionCode 单调递增；不要手工改 Gradle 推导逻辑
- 发布只能由 `v*` tag 触发 `Release` workflow；发布门禁：
  - `tools/audit-public-source.py` 公开源码审计
  - `tools/verify-release.py` 校验包名、版本契约、签名、ABI 机器类型、native 库、ksud 载荷
  - `SHA256SUMS` 随 Release 发布，管理器更新与隐藏态更新都会强制校验
- 不要 `git push --tags` 或改 release 配置来绕过门禁；签名密钥只存在于 CI Secrets

## 代码约定

- Kotlin：`spotless` + `ktlint`（`manager/` 下执行 `./gradlew spotlessApply`）；
  Compose 规则已配置，函数名/Modifier 命名按现有代码风格
- `ui-shared` 约束：只依赖 `androidx.compose.*` 与 material3，不引用 Android API；
  新增组件不得改动既有公开签名（Android 与浏览器版同时编译）
- Rust：`cargo fmt`、`cargo clippy`（`justfile` 有 clippy 任务）
- C/内核：遵循内核风格，最小 diff
- 提交信息：`type(scope): summary`（feat/fix/refactor/docs/ci/test）
- 不提交构建产物、APK、KO（`userspace/ksud/bin/` 下预编译 LKM 例外，见审计白名单）

## i18n

- 默认字符串在 `manager/app/src/main/res/values/strings.xml`
- 其余 `values-<locale>/strings.xml` 允许部分翻译，缺失项回退默认值
- 可翻译 key 必须补 `values-zh-rCN`、`values-zh-rHK`、`values-zh-rTW` 与主要语言
- 不要翻译 `translatable="false"` 的 key；新增 key 前先确认不冲突
- 占位符（`%1$s`、`%s`、`%d`）必须与默认值一致

## KPM（Kernel Patch Module，PoC）

- 代码：`manager/.../data/kpm` + `ui/screen/kpm`；后端 `userspace/ksud/src/kpm*.rs`
- 边界：内核侧 KPatch-Next 引擎未接入 → `engine_active` 恒 false；集成开关只持久化
  `reboot_required`，重启后 `transition_failed`；加载返回 `engine_inactive`/`kernel_error`
- 传输层仅 Android ARM64；其它架构必须返回 `unsupported`，不得伪造成功
- 数据：`/data/adb/ksu/kpm`，KPM 按 SHA-256 内容寻址；导入上限 32 MiB、仅 `.kpm`
- 命令：`ksud kpm status|list|import|info|load|control|unload|remove|autoload|integration`，
  统一 JSON envelope（`ok`/`data`/`error{code,message}`）；错误码要稳定、可展示
- 改动 KPM 时不要移除错误处理或降级为“成功”；x86_64 的 `unsupported` 是预期行为

## 模块仓库（registry 与自建源）

- 代码：`manager/.../data/module/registry`；协议为 KernelSU Modules API v1
  （`api/v1/index.json`，`schemaVersion=1`）
- 内置源由构建参数 `NEKOSU_REGISTRY_URL` 控制（必须 https 或空；空值禁用内置源）
- 自建源：KernelSU Modules / MMRL API，仅 https、URL 需规范化，持久化在
  `module_repository_sources`
- 不得放宽：`buildId` 一致性、路径安全（无 `..`）、release ZIP 仅 GitHub Releases
  来源、size + SHA-256 强校验、registry `updateJson` 的 versionCode/校验匹配
- 文档缓存写入 `files/module_registry`（自建源按 baseUrl SHA-256 分子目录）

## 模块 WebUI 扩展

- 代码：`manager/app/src/main/java/com/nekosu/ui/webui/`（`WebViewInterface` /
  `WebViewHelper` / `BlobDownloadHandler` / `Download.kt` / `MimeUtil`）与
  `manager/app/src/main/assets/download.js`；跨平台 UI 在 `webui-cmp/` 与 `ui-shared/`
- JS 协议（版本化，不要破坏）：
  - `ksu.exec` / `ksu.spawn`：root 命令执行；`spawn` 回调 emit stdout/stderr/exit
  - `ksu.io`（KSU_IO_VERSION = 1）：`File` / `FileInputStream` / `FileOutputStream` /
    `RandomAccessFile`，通过 root shell 访问文件
  - `ksu.pm`（KSU_PM_VERSION = 1）：`listPackages(type)` / `getPackagesInfo(json)`
  - `ksu_download`：`save` / `startChunkedDownload` / `writeDownloadChunk` /
    `completeChunkedDownload` / `cancelChunkedDownload` / `openExternal`
- 下载管线：JS Blob 分块（base64）→ 内部 URL（`blob-download.kernelsu.internal/<uuid>`）
  → 缓存临时文件（64 KiB 缓冲，5 分钟未完成清理）→ 系统下载目录
  （API 29+ `MediaStore.Downloads`，API 26~28 应用专属目录 + `FileProvider`）
- 不变量：外链仅 http/https；文件名必须清洗（去路径/`..`，空名回退 `download.bin`，
  重名追加 ` (1)`）；下载失败/完成通知复用下载渠道；改 JS 协议时同步 `download.js`、
  文档与 `WebUIDownloadTest`
- 新增/修改 UI 组件优先放 `ui-shared`（platform-neutral），浏览器版与 Android 共用

## 多 ABI（arm64-v8a / x86_64）

- `libksud.so` 按 ABI staging 到 `jniLibs/<abi>`；`NEKOSU_ABIS`/`ABIS` 选择，
  默认 arm64-v8a + x86_64；Release 发布通用包 + 分 ABI 包
- x86_64 能力边界：LKM/KO 资产仅 arm64、KPM 传输层仅 Android ARM64；x86_64 只能
  使用 UI/registry/下载/设置/KPM 管理界面，不能加载 LKM/KPM
- `verify-release.py` 需按 ABI 校验 native 库机器类型（AArch64 / x86_64）

## SUSFS 集成 / AVC

- `ksud susfs status|enable|disable`：enable 会立即加载 SUSFS guard LKM 并持久化
  开机自动加载；disable 停止自动加载并尝试卸载
- 状态字段：`supported`/`loaded`/`builtin`/`autoload`/`backend`/`version`/`variant`/`kmi`/
  `reason`/`rebootRequired`；builtin 时开关禁用
- 两层 AVC 开关互相独立：内核 `avc_spoof`（默认开启、实时值优先）与 SUSFS
  `avc_log_spoofing`（默认关闭、需手动 opt-in）

## 隐藏管理器更新

- 隐藏态只接受稳定版 Release 且必须带 `SHA256SUMS`；nightly/beta 一律拒绝并提示
  先恢复官方管理器（`hidden_manager_update_requires_restore`）
- 更新链路：Release `SHA256SUMS` → 下载 size/SHA-256 校验 → 官方签名/版本校验 →
  隐藏副本用保存的包名/图标/本地密钥重签名 → 身份不变 → 交给安装器
- `official.apk` 备份在**准备更新时**替换；取消安装后可通过通知重试已准备文件
  （`hidden_manager_update_retry`）；恢复允许安装较旧官方版本再走官方更新
- 隐藏/恢复固定 user 0；secondary 用户拒绝（`hidden_manager_multiuser_unsupported`），
  多用户设备提示 `hidden_manager_multiuser_note`
- 任一步失败必须中止并映射到 `hidden_manager_update_*` 文案；不得跳过校验
- versionCode 必须单调递增，否则更新被拒绝

## 安全与隐私

- 不提交签名密钥、token、设备私有信息、日志转储、本地路径
- 涉及权限、SELinux、su、隐藏管理器身份（`manager_identity`）的改动需在 PR 中
  说明威胁模型与回滚方式
- 隐藏管理器相关代码不得降低身份校验（包名 + 证书 SHA-256 + 一次性 token）
- registry/KPM/更新链路不得降低完整性校验（schema/buildId、size、SHA-256、签名、
  versionCode 单调性）
- 运行 `python3 tools/audit-public-source.py` 自检后再提交

## 代理工作流

1. 先读 `AGENTS.md`、`CONTRIBUTING.md` 与相关 `docs/`
2. 只做任务范围内的最小改动；跨模块改动先确认边界
3. 改完必须重新读取文件复核（重点：括号/import/占位符/包名）
4. 不要在受限环境（如无 Android SDK 的容器）强行跑 Gradle；改用静态检查并
   在交付说明中标注未编译验证
5. 交付时报告：改动文件、覆盖内容、未完成项与风险
