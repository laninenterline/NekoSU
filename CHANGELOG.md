
# 更新日志

## 未发布（Unreleased）

### 隐藏管理器

- 新增隐藏管理器：随机包名 + 本地自签名证书的管理器副本，可在设置内隐藏/恢复
- 内核按 `/data/adb/ksu/manager_identity` 记录的包名与证书 SHA-256 识别管理器
- 迁移流程使用一次性 token 与交接记录（`ready` / `authorized` / `failure`），
  新副本确认内核访问后才卸载原管理器；失败自动回滚
- 原始 APK 保存在管理器私有目录，支持恢复官方管理器
- 隐藏状态下的更新：只接受稳定版 Release + `SHA256SUMS`；用保存的包名/图标/本地
  密钥重签名，保留内核识别与 root；nightly/无校验和的更新要求先恢复官方管理器
- 准备更新即刷新保存的官方备份 APK（取消安装可重试），恢复支持先装回较旧官方
  版本再走官方更新
- 隐藏/恢复只支持 Android user 0，多用户设备给出提示；隐藏前重置 activity-alias
  自定义图标
- 迁移完成后提示重启，让内核重新加载并校验管理器身份

### AVC / SUSFS

- 设置新增 AVC 日志欺骗（`avc_spoof`）：区分 `supported` / `forced` / `managed` /
  `unsupported` 状态，仅内核支持时可切换；内核默认开启时以实时值为准
- SUSFS 新增 `avc_log_spoofing` 开关（默认关闭，需手动 opt-in）
- SELinux 隐藏、Su 日志、ADB Root 等内核特性统一走 `ksud feature check/get`

### 双 UI（Material / Miuix）

- 新增界面风格切换：Material 3 Expressive / Miuix，默认 Material
- 选择持久化在 `ui_mode`，模块 WebUI 页面跟随当前风格

### 模块 WebUI

- 补齐 WebUI JS 桥接：`exec` / `spawn`、`ksu.io` 文件 API、`ksu.pm` 包信息 API
- 下载支持 Blob 分块写入、文件名清洗、重名自增；API 29+ 写入系统下载目录，
  API 26~28 写入应用专属下载目录；完成/失败通知复用下载渠道
- 外链仅允许 `http` / `https`，并校验 URL

### 发布校验与版本契约

- `tools/verify-release.py`：校验 applicationId、`version.properties` 版本契约、
  APK 签名与证书、ARM64/x86_64 native 库、ksud 载荷与 ABI
- `tools/audit-public-source.py`：发布前审计公开源码树，阻止私钥/构建产物泄露
- Release 附带 SHA256SUMS 并覆盖通用包与分 ABI 包；管理器更新下载时强制校验
  SHA-256（Release 有 SHA256SUMS 时，缺少对应条目即拒绝更新）
- `version.properties` 作为版本契约唯一来源；`versionCode` 由该文件提供且每次发布递增，
  `versionName` 必须与 tag 一致（校验时忽略前导 `v`）
- 发布 ABI 扩展为 arm64-v8a / x86_64：release workflow 同时发布通用包与分 ABI 包

### i18n 与测试

- 隐藏管理器、模块 WebUI 下载相关新增字符串补齐简体/繁体中文与主要语言
- 新增 JVM 单元测试：隐藏管理器包名/证书/token 校验、WebUI 下载文件名清洗与分块写入、
  SUSFS AVC opt-in 配置、UiMode 持久化契约

### KPM（PoC）

- 新增 KPM 管理面：底部导航入口（默认隐藏，可开启）、`.kpm` 导入、加载/控制/卸载/自动载入
- 新增 `ksud kpm` 命令族（status/list/import/info/load/control/unload/remove/autoload/integration）
  与统一 JSON envelope、错误码、内容寻址存储（`/data/adb/ksu/kpm`）
- PoC 边界：内核 KPatch-Next 引擎未接入，加载返回 `engine_inactive`/`kernel_error`；
  x86_64 返回 `unsupported`；暂无启动镜像合成/引擎升级/回滚

### 模块仓库（registry）与自建源

- KernelSU Modules API v1 客户端：index/modules/detail/readme、`schemaVersion`/`buildId`
  一致性校验、未知版本拒绝、单条失败降级
- 内置源可由 `NEKOSU_REGISTRY_URL` 配置或禁用；支持自建 KernelSU Modules / MMRL 源
- release ZIP 限制 GitHub Releases 来源并强校验 size + SHA-256；registry `updateJson`
  更新校验 versionCode/校验值；文档缓存与离线回退

### 多 ABI（x86_64 能力边界）

- 发布 arm64-v8a / x86_64 分 ABI 包 + 通用包；`libksud.so` 按 ABI staging，
  `NEKOSU_ABIS` 选择
- x86_64 面向模拟器/开发体验：LKM/KO 资产仅 arm64，KPM 传输层仅 Android ARM64；
  发布校验按 ABI 校验 native 库机器类型

### SUSFS 集成与 AVC opt-in

- 新增 SUSFS 集成开关：按需加载 LKM 并持久化开机自动加载
  （`ksud susfs status/enable/disable`，状态含 builtin/lkm、版本、KMI、rebootRequired）
- AVC 两层开关：内核 `avc_spoof`（默认开启、实时值优先）与 SUSFS
  `avc_log_spoofing`（默认关闭、需在 SUSFS 配置页手动 opt-in）

### 版本契约

- `versionCode` 改为由 `version.properties` 提供（每次发布递增，当前 `40002`），
  不再依赖提交数；`versionName` 必须与 tag 一致（校验忽略前导 `v`）
- 正常更新与隐藏态更新都依赖 versionCode 单调递增

### 工程

- 新增 `AGENTS.md`：仓库导航、构建/测试约定、发布与安全约束
- 文档补充隐藏管理器、双 UI、模块 WebUI、发布校验与版本契约；本轮补充 KPM、
  registry、多 ABI、SUSFS 集成/AVC、版本契约递增规则
- Quality workflow 拆分 lint/JVM 单测，新增「测试确实执行」门禁与单测无
  `android.*` 依赖检查，并上传测试报告
- i18n：KPM 字符串补 zh-rCN/zh-rHK/zh-rTW；隐藏管理器多用户/更新限制等新 key
  同步 zh-rCN/zh-rHK/zh-rTW 与主要语言；`settings_webui_*` 默认值改为英文基线

## v1.0.0 — 首个正式版本

这是 NekoSU 的唯一发行版，包含全部核心能力：

### Root 与内核

- 临时 root（越狱模式）：Magica 一键越狱、`ksud late-load` 手动越狱
- GKI LKM 加载 `kernelsu.ko`，按 KMI 自动匹配
- 内置 SUSFS LKM，支持隐藏路径、kstat、进程与 maps
- 4.19 / 4.14 / 4.9 / 4.4 预编译内核模块
- 5.4 无统一 GKI 镜像，支持导入自定义 `kernelsu.ko`

### 管理器

- 首页状态、版本信息、KMI 诊断
- 模块管理：安装、启用、禁用、卸载、Action、模块 WebUI
- 超级用户：按应用授权 ROOT/UMOUNT、自定义 Profile、模板
- 设置：Su 兼容模式、隐藏 SELinux、软重启、日志、主题、背景
- 日志中心：SU / Magica / Crash / Metamodule / Problem
- 简体中文、繁体中文与多语言支持

### 工程

- `manager/`、`userspace/ksud/`、`kernel/`、`uapi/` 分层
- GitHub Actions 构建 debug 与正式签名 APK
- 正式签名贯穿所有发行版，保证原地更新且 root 保留
