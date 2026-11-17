
# NekoSU

NekoSU 是一个面向**临时 root（越狱模式）**的 Root 管理器，基于 KernelSU 与 SUSFS 构建。
它不刷写 boot 分区、不修改内核镜像，通过越狱入口在运行时加载 `kernelsu.ko`，
在重启后自动失效，需要重新越狱。

> 当前正式版本：**v1.0.0**。`version.properties` 是版本契约的唯一来源，
> 版本号推导与发布校验见 [发布流程](docs/RELEASING.md)。

## 特性

- 临时 root：Magica 一键越狱 / 手动 `ksud late-load`，重启失效，不落盘 boot
- GKI LKM 管理：加载 `kernelsu.ko`，按内核版本匹配 KMI
- SUSFS：内置 SUSFS LKM（按 KMI 加载），支持隐藏路径 / kstat / 进程 / maps
- 4.x 兼容：内置 4.19 / 4.14 / 4.9 / 4.4 预编译 KO
- 模块管理：安装、启用/禁用、卸载、Action 脚本、模块 WebUI
- 模块 WebUI 桥接：`exec` / `spawn` 命令、`ksu.io` 文件 API（File / 流 / RandomAccessFile）、
  `ksu.pm` 包信息 API、Blob 分块下载到系统下载目录、仅 http/https 外链
- KPM（Kernel Patch Module，PoC）：底部导航 KPM 入口、`.kpm` 导入/加载/控制/卸载/自动载入，
  集成开关与状态、错误码；内核侧 KPatch-Next 引擎尚未接入，当前不可真正加载（详见架构）
- 模块仓库：内置 KernelSU Modules API v1 registry（`NEKOSU_REGISTRY_URL` 可配置/禁用），
  支持自建 KernelSU Modules / MMRL 源；release 下载仅允许 GitHub Releases 并强校验
  size + SHA-256，带文档缓存与离线回退
- 超级用户：按应用授权 ROOT、UMOUNT、自定义 Profile 与模板
- 隐藏管理器（Hide Manager）：安装随机包名、本地自签名证书的管理器副本；内核按
  `manager_identity` 记录的包名与证书识别管理器；官方 APK 保留，可随时恢复；
  隐藏状态下的管理器更新在本地重签名，保留内核识别与 root
- 双 UI：Material 3 Expressive 与 Miuix 两套界面风格可切换并持久化，模块 WebUI 跟随
- AVC / SUSFS：SUSFS 集成开关（按需加载 LKM + 开机自动加载）；内核 AVC 日志欺骗
  （`avc_spoof`，需内核支持）与 SUSFS `avc_log_spoofing`（默认关闭、手动 opt-in）；
  特性状态区分 `supported` / `forced` / `managed` / `unsupported`
- 发布校验：`tools/verify-release.py` 校验包名、版本契约、签名与内嵌 native 库，
  `tools/audit-public-source.py` 审计公开源码树，Release 附带 SHA256SUMS
- 设置：Su 兼容模式、隐藏 SELinux、ADB Root、日志中心、KMI 诊断、主题与界面风格
- 日志中心：SU / Magica / Crash / Metamodule / Problem

## 支持范围

| 项 | 范围 |
|---|---|
| Android | 7.0+（管理器最低 API 26） |
| 内核 | 4.4 / 4.9 / 4.14 / 4.19 / 5.4 / 5.10 / 5.15 / 6.1 / 6.6 / 6.12 |
| Root 模式 | **仅临时 root（越狱模式）** |
| ABI | arm64-v8a / x86_64（Release 同时提供通用包与分 ABI 包，见下方说明） |
| KPM | PoC：管理面可用；需内核 KPatch-Next 引擎（当前未接入），x86_64 仅管理界面 |
| 模块仓库 | KernelSU Modules API v1（默认 modules.kernelsu.org）+ 自建源 |
| 界面风格 | Material 3 Expressive / Miuix（设置内切换） |
| 更新 | 仅同签名 APK 覆盖；隐藏管理器在本地重签名后覆盖 |
| 发布校验 | 签名校验 + `version.properties` 契约 + SHA256SUMS（管理器更新会校验） |

> **x86_64 能力边界**：管理器与模块仓库功能不区分 ABI；x86_64 的 `kernelsu.ko`
> 由 DDK x86_64 构建（Release 中为 best-effort），`ksuinit` 会构建并嵌入 ksud，
> 因此可以用 `ksud late-load` 加载 LKM。上游 SUSFS LKM 只支持 arm64，x86_64 没有
> SUSFS LKM；boot image capsule 补丁与 KPM 传输层仍只支持 arm64。riscv64 只产出
> ksud artifact，不打包进管理器。

## 快速开始

1. 从 GitHub Releases 下载 `NekoSU_v<version>_<versionCode>-<abi>-release.apk`
   （或通用包 `NekoSU_v<version>_<versionCode>-universal-release.apk`）安装
2. 打开 NekoSU，在首页点击「越狱」，或手动执行：
   ```bash
   su -c "ksud late-load"
   ```
3. 越狱成功后首页显示「工作中 / LKM / 越狱模式」
4. 重启后临时 root 失效，重新执行越狱

详见 [临时 root 说明](docs/TEMPORARY-ROOT.md)。

## 隐藏管理器

设置 → 工具 → 隐藏管理器 可以把管理器替换为随机包名、本地自签名证书的副本：

- 内核通过 `/data/adb/ksu/manager_identity` 中记录的包名 + 证书 SHA-256 识别管理器
- 迁移使用一次性 token + 交接记录，新副本确认内核访问后才会卸载原管理器
- 原始 APK 保存在管理器私有目录，可恢复官方管理器
- 隐藏状态下的更新只接受**稳定版 Release + `SHA256SUMS` 校验**：先用保存的包名/
  图标/本地密钥重签名，保留内核识别与 root；nightly/无校验和的更新会要求先恢复
  官方管理器。准备更新时就会刷新官方备份 APK（即使取消安装也可重试）
- 隐藏/恢复只支持 Android user 0；设备有其他用户时会提示
- 迁移完成后建议重启一次，让内核重新加载并重新校验管理器身份

风险与恢复步骤见 [隐藏管理器说明](docs/HIDDEN_MANAGER.md)、
[临时 root 说明](docs/TEMPORARY-ROOT.md) 与 [FAQ](docs/FAQ.md)。

## KPM（Kernel Patch Module，PoC）

KPM 是内核补丁模块加载器，入口在底部导航（默认隐藏，可开启）。当前为 PoC：

- 可导入 `.kpm`（≤ 32 MiB，按 SHA-256 内容寻址）、查看/加载/控制/卸载/自动载入管理
- 集成开关由 `ksud kpm integration enable|disable` 持久化；启用后需要重启，重启后若
  内核仍无 KPatch-Next 引擎则显示「集成错误」，加载返回 `engine_inactive`/`kernel_error`
- 传输层只在 Android ARM64 编译；x86_64 返回 `unsupported`
- 未实现启动镜像合成/引擎升级/回滚，详见 [架构](docs/ARCHITECTURE.md#kpmkernel-patch-modulepoc)

## 模块仓库（registry）

- 协议：KernelSU Modules API v1（`api/v1/index.json`，schemaVersion=1，buildId 一致性校验）
- 内置源默认 `https://modules.kernelsu.org/`；构建时 `-PNEKOSU_REGISTRY_URL=https://...`
  可覆盖，空值禁用内置源，此时仍可使用自建源
- 「添加仓库」支持自建 KernelSU Modules / MMRL 源（仅 https）
- release ZIP 不限定托管域名：任意 https 地址（GitHub Releases、自建域名或 CDN）
  均可，但 detail 必须给出准确的 `size` + `sha256`，安装前强制校验；
  registry 的 `updateJson` 更新必须匹配 registry release 的 versionCode 与校验值
- 最后一份完整文档会缓存，网络失败时离线回退

## 双 UI（Material / Miuix）

- 设置 → 应用设置 → 界面风格 在 Material 3 Expressive 与 Miuix 之间切换
- 选择持久化在 `ui_mode`（`material` / `miuix`），默认 `material`
- 模块 WebUI 页面同样跟随当前界面风格

## 更新

- 应用内更新只接受与当前安装**同签名**的正式版 APK，原地覆盖，root 保留
- Release 附带 `SHA256SUMS`；管理器选择 APK 后强制校验 SHA-256（有该资产时缺失条目即拒绝更新）
- 不同签名的安装包（例如自行编译的 debug 版）会要求先卸载，导致临时 root 丢失
- 隐藏管理器在隐藏状态下会拒绝非同签名/非更高版本的更新，并在本地重签名后安装
- 手动校验下载：见 [发布流程](docs/RELEASING.md) 的 SHA256SUMS 一节

## 构建

推荐使用 GitHub Actions：

- `Build Manager (debug)`：手动触发 debug APK
- `Quality`：lint + 单元测试
- `Release`：推送 `v*` tag 触发正式签名 APK

本地构建见 [构建文档](docs/BUILDING.md)；仓库约定见 [AGENTS.md](AGENTS.md)。

## 版本契约

- `version.properties` 是唯一版本来源：`NEKOSU_VERSION_NAME` / `NEKOSU_VERSION_CODE`
- **每次发布递增 `NEKOSU_VERSION_CODE`**（当前 `40002`）；`NEKOSU_VERSION_NAME` 必须与
  发布 tag 一致（校验时忽略前导 `v`）
- Gradle 直接读取该文件；文件缺失时才回退旧的 `40000 + 提交数` / `git describe` 逻辑
- 管理器更新与隐藏态更新都依赖 versionCode 单调递增
- Release 由 tag 触发；构建产物必须与 `version.properties` 一致才能发布

## 仓库结构

```
manager/            Android 管理器（Kotlin + Compose）
ui-shared/          Android 与浏览器版共用的 Compose 组件（platform-neutral）
webui-cmp/          Compose Multiplatform 浏览器版 UI
userspace/ksud/     ksud（Rust）
kernel/             内核侧补丁与 SUSFS 源码
uapi/               内核与用户态共享头文件
tools/              发布校验、源码审计、APK 重打包脚本
scripts/            本地构建辅助脚本
docs/               文档
version.properties  版本契约
.github/workflows/  CI
```

## 文档

- [文档索引](docs/README.md)
- [架构](docs/ARCHITECTURE.md)
- [构建](docs/BUILDING.md)
- [内核与 KMI](docs/KERNELS.md)
- [临时 root](docs/TEMPORARY-ROOT.md)
- [发布流程](docs/RELEASING.md)
- [FAQ](docs/FAQ.md)
- [故障排查](docs/TROUBLESHOOTING.md)
- [仓库约定（AGENTS）](AGENTS.md)

## 许可证

本项目基于 KernelSU（GPL-3.0）与 SUSFS（GPL-2.0）构建，遵循对应上游许可证。
