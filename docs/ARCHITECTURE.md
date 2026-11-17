
# 架构

## 总览

```
┌────────────────────────────────────────────────┐
│ manager (Android/Kotlin)                       │
│  ui/（Material 3 + Miuix 双实现）              │
│  ui-shared/（与浏览器版共享的 Compose 组件）   │
│  viewmodel/  domain/  data/                    │
│    ├─ Root 通道：libksud.so / su               │
│    ├─ 隐藏管理器：HiddenManagerRepository      │
│    ├─ 模块仓库：data/module/registry           │
│    ├─ KPM 管理面：data/kpm（PoC）              │
│    └─ 模块 WebUI：WebView + JS 桥接            │
└───────────────┬────────────────────────────────┘
                │ libksud / su
┌───────────────▼────────────────────────────────┐
│ ksud (Rust, userspace)                         │
│  越狱、LKM、模块、profile、SUSFS 集成、KPM     │
└───────────────┬────────────────────────────────┘
                │ ioctl / syscall
┌───────────────▼────────────────────────────────┐
│ kernelsu.ko + SUSFS LKM                        │
│  su、allowlist、挂载、隐藏、manager_identity   │
│  KPM 需要 KPatch-Next 引擎（当前未接入）       │
└────────────────────────────────────────────────┘
```

## 组件

### manager

- `manager/app`：Compose 界面、ViewModel、Repository
- Root 通道：`libksud.so`（按 ABI 打包的 ksud）；设置与内核特性通过
  `ksud feature check/get` 读取
- 设置持久化：`AppSettingsRepository`（DataStore）+ `ThemeRepository`
- 模块、超级用户、设置、日志中心的全部交互都通过 ksud / 内核接口完成
- KPM：`data/kpm`（协议/仓库/导入/本地入口开关）与 `ui/screen/kpm`
- 模块仓库：`data/module/registry`（协议解析、完整性校验、缓存、更新策略）
- SUSFS 集成：`data/susfs` + `domain/model/SusfsIntegration`

### ui-shared

`ui-shared/src/commonMain` 是 platform-neutral 的 Kotlin/Compose 组件
（包名 `com.nekosu.shared`），同时被 Android 管理器与 `webui-cmp`（Compose
Multiplatform 浏览器版）编译。只允许依赖 `androidx.compose.*` 与 material3，
不引用 Android API；新增组件时不得改动既有公开签名。

### userspace/ksud

- `late-load`：加载 `kernelsu.ko`、恢复 SELinux、执行模块脚本
- Magica 引导：在 app zygote 中执行 ksud，临时开启 adbd root，完成 late-load 后恢复属性
- 模块管理、profile、allowlist、日志写入
- `manager-identity probe|set|clear`：维护内核识别管理器所需的包名 + 证书身份
- `susfs status|enable|disable`：SUSFS 集成开关（按需加载 LKM + 开机自动加载）
- `kpm ...`：KPM 导入/加载/控制/卸载管理（PoC，见下）

### kernel/

- `kernel/susfs`：SUSFS 源码（LKM 化）
- 内核侧 hook、supercall、路径/进程隐藏实现
- `manager_identity` 校验：内核按包名 + 证书 SHA-256 判断当前管理器是否可信
- KPM 依赖 KPatch-Next 内核引擎；本仓库内核尚未接入，因此 KPM 仅完成用户态管理面（PoC）

### uapi/

内核与用户态共享的命令、结构体、特性位。

### tools/ 与 scripts/

- `tools/verify-release.py`：发布 APK 校验（包名、版本契约、签名、ABI、native 库、ksud 载荷）
- `tools/audit-public-source.py`：发布前公开源码树审计
- `tools/repack_*.py`、`scripts/build-*.sh`：本地构建与重打包辅助

## 临时 root 生命周期

1. 用户点击「越狱」→ 启动 Magica 服务
2. app zygote 中执行 ksud → 临时获得受限 root
3. 短暂开启 adbd root，执行 `ksud late-load`
4. `late-load` 加载 `kernelsu.ko` + SUSFS LKM，初始化模块与 profile
5. 恢复 adbd 属性，管理器通过 `su` 使用 root
6. 重启后内核模块消失，root 失效，需要重新越狱

## 隐藏管理器迁移

隐藏管理器把当前管理器替换为**随机包名 + 本地自签名证书**的副本，内核仍然把
它当作合法管理器，但包名与签名不再是公开的官方值。

### 状态与记录

- 当前安装包名 == `BuildConfig.APPLICATION_ID` → 官方管理器；否则为隐藏副本
- 身份记录（333 字节，little endian）：`u32 magic | u32 version | u32 cert_size |
  char package[256] | char cert_sha256[65]`，通过 `ksud manager-identity set` 写入
  `/data/adb/ksu/manager_identity`
- 迁移记录 `ManagerTransfer`：一次性 64 位十六进制 token、源/目标包名、源/目标 UID、
  版本、双端证书 SHA-256、isHidden、创建时间；`validate()` 校验 token、包名、
  UID 范围、时间窗口（300 秒）、证书格式
- 交接文件写在**目标应用私有目录** `files/hidden_manager/`：
  `transfer.json`、`ready`、`authorized`、`failure`；原始 APK、本地密钥、
  外观记录（`official.apk` / `identity.p12` / `appearance.json` / `icon.png`）同样保留

### 迁移流程

1. 校验运行中 APK 身份与包名，生成未被占用的随机包名（`com.<word>.<word>`，
   过滤 su/io/manager 等敏感词）
2. 以目标包名、应用名、图标重打包并自签名；要求 versionCode 不降级、
   非 debuggable/testOnly/split、签名与本地密钥一致
3. 写入 `manager_identity`（隐藏时）与 `transfer.json`，通过 `pm install -r` 安装副本
4. 新副本启动后校验 `transfer.json`、内核 `Natives.isManager`，写 `ready`；
   源管理器确认 token 后写 `authorized`，再卸载源管理器
5. 任一步失败：反注册身份、卸载新副本、删除临时目录，原管理器保留并记录 `failure`

迁移完成后建议重启，让内核重新加载并重新校验管理器身份。

### 隐藏状态下的管理器更新

隐藏状态下的更新与普通更新共用下载链路，但多一层“重签名回隐藏身份”的约束：

1. `ManagerUpdateRepository` 读取 Release 资产中的 `SHA256SUMS`；Release 带有该资产
   但目标 APK 没有条目时直接拒绝更新。下载完成后 `DownloadService` 强校验 size +
   SHA-256（安装前删除不匹配文件）。
2. 隐藏管理器不使用普通 nightly/beta 更新（它们没有 Release `SHA256SUMS` 与官方
   签名）；界面提示先恢复官方管理器，不会进入下载失败流程。
3. `HiddenManagerRepository.repackageManagerUpdate` 对官方稳定版 APK 二次校验：
   必须是官方签名、非 debug/testOnly/split、versionCode 更高；随后用**相同包名、
   图标与本地密钥**重签名，要求重签名后证书仍与当前隐藏身份一致。
4. 准备更新时即替换保存的 `official.apk` 备份（不是等安装完成）；即使用户取消
   安装提示，备份已刷新，可通过通知重新安装已准备好的文件（
   `hidden_manager_update_retry`）。
5. 任一步失败都中止更新并上报对应 `hidden_manager_update_*` 错误。若隐藏副本被
   手动升级导致备份更旧，恢复流程允许先安装较旧的官方版本，再用官方更新补齐
   （界面会说明）。

多用户限制：迁移与恢复固定使用 Android user 0；管理器运行在 secondary 用户下时
直接拒绝并提示 `hidden_manager_multiuser_unsupported`，设备存在其他用户时展示
`hidden_manager_multiuser_note`。隐藏前若检测到 activity-alias 自定义图标，会先
切回默认图标，避免隐藏后仍暴露旧图标。

因为 `versionCode` 现在来自 `version.properties`，发布时必须保证 versionCode 单调
递增，否则更新链路与隐藏态更新都会被拒绝（见「发布校验与版本契约」）。

## 双 UI（Material 3 / Miuix）

- `UiMode`（`com.nekosu.ui`）：`Material("material")` 与 `Miuix("miuix")`，默认 Material
- 持久化：`ThemeRepository.saveUiMode/loadUiMode`，DataStore key `ui_mode`
- 生效：`ThemeConfig.uiMode` + `LocalUiMode` 注入 Compose 树；`KernelSUTheme`
  在 Material Expressive 与 Miuix 主题之间切换
- 页面：首页、设置、关于等提供 Miuix 变体；模块 WebUI 页面同样跟随 `ui_mode`
- 组件层：`ui-shared` 组件不感知 UiMode，两套 UI 复用同一份数据模型

## 模块 WebUI 桥接

模块 WebUI 在 `WebUIActivity`（Intent 参数 `id` 指定模块目录）中打开，由
`WebViewHelper` 注入 `download.js` 与 JS 接口。所有接口在应用进程内运行，
文件操作使用 root shell，因此模块脚本与网页共享 root 能力。

### JS 接口

| 接口 | 说明 |
|---|---|
| `ksu.exec` / `ksu.spawn` | 以 root 执行命令；`spawn` 通过回调 emit stdout/stderr/exit |
| `ksu.io`（version 1） | `File`、`FileInputStream`、`FileOutputStream`、`RandomAccessFile` |
| `ksu.pm`（version 1） | `listPackages(type)`、`getPackagesInfo(json)` |
| `ksu_download` | `save`、`startChunkedDownload`、`writeDownloadChunk`、`completeChunkedDownload`、`cancelChunkedDownload`、`openExternal` |
| 其他 | `toast`、`fullScreen`、`moduleInfo`、`exit` |

### 下载管线

1. JS 侧把 Blob 切成 base64 分块，`startChunkedDownload` 在缓存目录创建临时文件并
   返回内部 URL（`blob-download.kernelsu.internal/<uuid>`）
2. `writeDownloadChunk` 逐块写入（64 KiB 缓冲），`completeChunkedDownload` 关闭流
3. `saveChunkedDownload` 只在第一次取走完成项，写入系统下载目录：
   API 29+ 使用 `MediaStore.Downloads`（`IS_PENDING` 原子发布）；API 26~28 使用
   应用专属外部下载目录 + `FileProvider`
4. 文件名经清洗（去掉路径与 `..`，空名回退 `download.bin`）；重名自动追加
   ` (1)`、` (2)`；完成/失败/进度通知复用下载渠道
5. 未完成的临时文件超过 5 分钟会被清理；外链只允许 `http` / `https`

## KPM（Kernel Patch Module，PoC）

KPM 页面与用户态管理链路已落地，但**内核侧 KPatch-Next 引擎尚未接入 NekoSU 内核**，
因此当前是 PoC：可导入、查看、管理记录，不能真正加载模块。边界写在
`userspace/ksud/src/kpm_image.rs` 的注释中，UI 只展示状态与错误，不会崩溃。

### PoC 边界

- `engine_active` 恒为 false（内核没有 KPatch-Next 引擎）
- 启用集成只持久化开关：同一次开机内 `reboot_required = true`；重启后引擎仍不存在，
  则 `transition_failed = true`，UI 显示「集成错误」
- 加载 KPM 时后端返回明确的 `engine_inactive` / `kernel_error` 错误
- 未实现启动镜像合成、引擎升级/回滚（PoC 替代上游 MilkSU 的完整 `kpm_image`）
- 传输层只在 **Android ARM64** 编译（复用 syscall + KPatch `compact_cmd`）；
  其他架构返回 `unsupported`（"KPM requires an Android ARM64 kernel"）
- 因此 x86_64 只能使用 KPM 管理界面，不能加载 KPM

### 命令（`ksud kpm ...`）

| 命令 | 说明 |
|---|---|
| `kpm status [--runtime-only]` | 集成 + 运行时状态 JSON |
| `kpm list` | 已导入 KPM 列表 |
| `kpm import <path>` | 导入 `.kpm`（≤ 32 MiB，非空；按内容寻址，id = SHA-256） |
| `kpm info <id>` | 单个 KPM 详情 |
| `kpm load <id> [--args <args>]` | 加载（需要集成 active；失败返回 engine_inactive/kernel_error） |
| `kpm control <id> --args <args>` | 调用 KPM 的控制接口（`has_control` 才允许） |
| `kpm unload <id>` | 卸载（重启后生效，记录 `pending_unload`） |
| `kpm remove <id>` | 移除已导入记录 |
| `kpm autoload <id> enable\|disable` | 开机自动加载开关 |
| `kpm integration enable\|disable` | 持久化 KPM 集成开关（需重启生效） |

所有命令返回统一 envelope：`{"ok": bool, "data": ..., "error": {"code","message"} | null}`；
失败码包括 `engine_inactive`、`kernel_error`、`reboot_required`、`unsupported`、
`invalid_argument`、`not_found`、`already_loaded`、`unconfirmed_load`、
`unconfirmed_autoload`、`unconfirmed_remove`、`file_too_large`、`empty_file`、`invalid_file` 等。

### 状态与数据

- `KpmStatus`：`supported`、`configured_enabled`、`active`、`reboot_required`、
  `transition_failed`、`state_known`、`version`、`reason`、`can_configure`、
  `can_enable`/`can_disable`、`integration_verified`、`can_unload`、`engine_sha256`
- UI 状态映射：READING / OFF / PENDING_REBOOT / ACTIVE / UNAVAILABLE / ERROR
  （`transition_failed` 或 configured != active 视为 ERROR）
- 后端状态目录：`/data/adb/ksu/kpm`（`integration.json` schema 1 + boot_id，
  KPM 记录按 SHA-256 内容寻址；记录包含 id/args/autoload/pending_unload/last_error 等）
- 加载语义：临时加载 = 现在加载、不自动载入；持久加载 = 加载 + 开机自动载入；
  已加载且未重启的 KPM 卸载会标记 `pending_unload`，重启后完成
- 导入限制在 `KpmDocument.MAX_BYTES = 32 MiB`，前端只接受 `.kpm` 文档

## 模块仓库（registry 与自建源）

### 协议（KernelSU Modules API v1）

- 基址 `baseUrl` 必须是 https；入口 `api/v1/index.json`，文档路径均相对站点根
- 协议版本 `schemaVersion = 1`；未知版本直接拒绝（提示升级管理器）
- 文档链：index → `api/v1/modules.json`（模块列表）→ `api/v1/modules/{id}.json`
  （详情：releases/readme/compatibility/source/health）→ README HTML 片段
- `buildId` 一致性：index 与列表必须来自同一 build；detail 的 buildId 不一致时会
  重读 index/列表一次，仍不一致则失败（绝不混用两个 build 的数据）
- 未知字段忽略；可安全降级的条目（单个模块/单个 release）解析失败时丢弃而不是整份失败
- 下载完整性：release ZIP 必须是 https（无 userinfo），重定向目标同样必须是 https；
  不限定托管域名（GitHub Releases、自建域名或 CDN 均可），但 ZIP 的 `size` 与
  `sha256` 由 registry 文档固定，安装前强制流式校验，host 本身不提供完整性保证
- `updateJson` 更新策略：若 updateJson 托管在 registry 站点、ZIP 与 registry 同源或
  ZIP 位于 github.com，必须匹配 registry release 的 versionCode 与校验值，否则拒绝；
  非 registry 的旧式 updateJson 保持原有路径
- 缓存/离线：最后一份完整一致的文档缓存在应用私有 `files/module_registry`
  （自建源按 baseUrl SHA-256 放入 `sources/<hash>` 子目录）；网络/解析失败时回退缓存

### 内置 registry 与自建源

- 内置源默认 `https://modules.kernelsu.org/`（KernelSU Modules 协议），可在构建时用
  `-PNEKOSU_REGISTRY_URL=https://...`（或同名环境变量）覆盖；空值禁用内置源。覆盖后
  内置源在仓库列表里显示为该 URL 的 host（官方地址仍显示 KernelSU Official）
- 自建源：模块仓库页「Add repository」可添加 KernelSU Modules API 或 MMRL API 源；
  仅接受 https、无 userinfo，URL 会被规范化；列表持久化在 DataStore
  `module_repository_sources`；删除内置源外，自建源可增删改
- 自建 registry 只需提供 schemaVersion=1 的 index/modules/detail/readme 文档；
  release ZIP 可以是任意 https 地址（自有域名/CDN/GitHub Releases），只要 detail
  中提供正确的 `size` + `sha256`，管理器都会强制校验后安装
- 内置源为空时仍可完全使用自建源

## SUSFS 集成与 AVC opt-in

### SUSFS 集成开关

- 设置 → 配置 → SUSFS 集成：调用 `ksud susfs status --package-name <pkg>` 读取状态
- `enable`：立即加载 SUSFS guard LKM，并持久化为下次开机自动加载
- `disable`：停止开机自动加载，并尝试卸载当前 LKM
- 状态字段：`supported`、`loaded`、`builtin`、`autoload`、`backend`
  (`builtin` / `lkm` / `none`)、`version`、`variant`、`kmi`、`reason`、`rebootRequired`
- 内核已内置 SUSFS（`builtin`）时开关禁用并展示内置版本；`rebootRequired` 时提示
  重启；`autoload` 但尚未 loaded 时提示等待加载；`package_name` 用于定位用户导入的
  自定义 SUSFS LKM

### AVC opt-in（两层，互相独立）

- 内核特性 `avc_spoof`：默认开启，以实时值（`ksud feature get avc_spoof`）为准；
  状态 `supported`/`forced`/`managed`/`unsupported`，只有 `supported` 时开关可用
- SUSFS 配置 `avc_log_spoofing`：默认 **false**，需在 SUSFS 配置页手动开启
  （「AVC 日志欺骗」）；开启后替换 SELinux audit 日志中的 su tcontext
- 两者互不替代：内核特性处理 audit 日志中的 root 域，SUSFS 配置是 SUSFS 自己的
  欺骗开关；设备/内核不支持时保持关闭并显示原因

## AVC / SUSFS 与内核特性状态

- 内核特性通过 `ksud feature check <name>` 返回状态：
  `supported` / `managed` / `forced` / `unsupported`
- 设置页只有 `supported` 时才允许切换；`forced` 由内核/启动配置强制，仅展示说明
- SELinux 隐藏、Su 日志、ADB Root、Su 兼容模式等同样走特性状态模型

## 多 ABI（arm64-v8a / x86_64）

### 打包

- `libksud.so` 按 ABI staging 到 `manager/app/src/main/jniLibs/<abi>/`
- `NEKOSU_ABIS`（或 `ABIS`）选择打包 ABI，默认 arm64-v8a + x86_64；
  没有 staged daemon 的 ABI 不会打包（IDE-only 构建回退 arm64-v8a）
- Release 同时发布通用包与分 ABI 包；`SHA256SUMS` 覆盖全部三类 APK
- `verify-release.py` 按 ABI 校验 native 库机器类型（AArch64 / x86_64）与必需条目

### 能力边界

- arm64-v8a：各 KMI 的 `kernelsu.ko` 与 `susfs_guard_lkm.ko` 打包进
  `userspace/ksud/bin/aarch64/`，临时 root/LKM/SUSFS/KPM 完整可用
- x86_64：Release 用 DDK x86_64 构建 `kernelsu.ko`（best-effort，失败不阻塞 arm64
  发布），`ksuinit` 构建并嵌入 ksud，可用 `ksud late-load` 加载 x86_64 `kernelsu.ko`；
  上游 `susfs4ksu-lkm` 的 `module.yaml` 仅声明 `arm64-v8a`，源码含大量 arm64 专属实现
  （另有非官方 x64 fork，未接入），因此 x86_64 没有 SUSFS LKM
- boot image 补丁（`ksud boot-patch` 的 capsule/LKM image 路径）目前只实现 arm64
- KPM 传输层只在 Android ARM64 编译，x86_64 返回 `unsupported`
- riscv64：只构建 ksud artifact，不打包进 Manager APK
- 管理器更新按设备 `SUPPORTED_ABIS` 选包，优先精确 ABI，通用包兜底；
  Release 使用 SHA256SUMS 时缺失目标条目会拒绝更新

## 数据目录

| 路径 | 内容 |
|---|---|
| `/data/adb/ksu` | allowlist、模块列表、配置、`manager_identity` |
| `/data/adb/ksu/kpm` | KPM 集成状态（`integration.json`）与内容寻址的 KPM 记录 |
| `/data/adb/modules` | 已安装模块 |
| `/data/adb/ksud` | ksud 与运行状态 |
| 应用私有 `files/hidden_manager` | 原始 APK、本地密钥、迁移记录、外观（隐藏管理器） |
| 应用私有 `cache/hidden_manager_updates` | 隐藏管理器更新重签名临时文件 |
| 应用私有 `files/module_registry` | registry 文档缓存（自建源位于 `sources/<hash>`） |
| 应用私有 DataStore | 设置、`ui_mode`、主题、`module_repository_sources` 等偏好 |

## 发布校验与版本契约

- `version.properties` 是唯一版本来源：`NEKOSU_VERSION_NAME` / `NEKOSU_VERSION_CODE`
- Gradle 直接读取该文件（缺失时才回退旧的 `40000 + 提交数` / `git describe` 逻辑）；
  **每次发布必须递增 `NEKOSU_VERSION_CODE`**，更新链路依赖 versionCode 单调递增
- `NEKOSU_VERSION_NAME` 必须与发布 tag 一致（校验容忍 APK 侧前导 `v`）
- `tools/verify-release.py` 发布前校验：applicationId、版本契约、签名证书、
  ABI 集合与机器类型、必需 native 库、ksud 构建标识、非 debug/testOnly
- `tools/audit-public-source.py` 阻止私钥/构建产物进入公开源码树
- Release 生成 `SHA256SUMS`；管理器下载更新后强制校验 SHA-256；隐藏态更新还会
  额外校验官方签名并重签名
- 发布必须由 tag 触发；构建产物与 `version.properties` 不一致即失败

## 设计约束

- 不修改 boot 分区，不依赖开机镜像
- 所有 root 能力必须能通过一次越狱 + late-load 获得
- 管理器更新必须保持同签名，避免卸载导致 allowlist 丢失
- 隐藏管理器迁移必须具备可回滚、可恢复官方管理器的路径
- `ui-shared` 组件必须同时能在 Android 与 Compose Multiplatform 编译
- KPM/registry/多 ABI 扩展不得削弱完整性校验（签名、SHA-256、schema/buildId 一致性）
