
# FAQ

**Q：NekoSU 支持永久 root 吗？**
A：不支持。只提供临时 root（越狱模式），重启后失效。

**Q：更新管理器会掉 root 吗？**
A：正式版同签名覆盖更新不会掉 root。debug 版或换签名安装包需要卸载重装，会掉 root。

**Q：为什么重启后 root 没了？**
A：临时 root 由运行时加载的内核模块提供，重启即清除，需要重新越狱。

**Q：支持哪些内核？**
A：4.4–6.12，详见 [内核与 KMI](KERNELS.md)。5.4 需要导入自定义 KO。

**Q：支持哪些 CPU 架构？x86_64 能做什么？**
A：Release 提供 arm64-v8a、x86_64 的分 ABI 包与通用包，管理器功能（设置、模块仓库、
下载、日志等）不区分 ABI。arm64-v8a 是完整支持（临时 root、LKM、SUSFS、KPM）；
x86_64 由 DDK 构建 `kernelsu.ko`（Release 中 best-effort，可能缺席）并构建
`ksuinit` 嵌入 ksud，可以用 `ksud late-load` 加载 LKM；但上游 SUSFS LKM 只有 arm64
资产，因此 x86_64 **没有 SUSFS LKM**，boot image capsule 补丁与 KPM 传输层也仍只支持
arm64。riscv64 只产出 ksud artifact，不打包管理器。管理器按设备 `SUPPORTED_ABIS`
选择匹配的更新包。

**Q：模块 WebUI 是什么？**
A：模块自己提供的网页界面，在管理器内打开，与 NekoSU 无关。它通过 JS 桥接
获得 root 命令执行、文件读写、包信息查询与下载能力；下载会保存到系统下载目录
（API 26~28 为应用专属下载目录），外链仅允许 http/https。

**Q：模块 WebUI 下载的文件在哪里？**
A：Android 10（API 29）及以上在系统「下载」目录，Android 8.0~9（API 26~28）在
应用专属下载目录。重名文件会自动追加 ` (1)`、` (2)`；下载完成/失败有通知。

**Q：如何查看日志？**
A：设置 → 工具 → 日志中心，可按 SU / Magica / Crash / Metamodule / Problem 过滤。

**Q：版本号如何递增？**
A：`version.properties` 是唯一版本来源：`NEKOSU_VERSION_NAME` 必须与发布 tag 一致，
`NEKOSU_VERSION_CODE` **每次发布都要递增**（当前为 `40002`）。管理器更新与隐藏态
更新都依赖 versionCode 单调递增；不再使用 `40000 + 提交数` 推导。详见
[发布流程](RELEASING.md)。

**Q：隐藏管理器是什么？**
A：把管理器替换成随机包名、本地自签名证书的副本（设置 → 工具 → 隐藏管理器）。
内核通过 `/data/adb/ksu/manager_identity` 记录的包名与证书 SHA-256 识别它，
官方 APK 会保留以便恢复。

**Q：隐藏管理器有什么风险？**
A：更改包名与签名证书可能导致内核不再识别管理器或阻止后续更新。迁移期间请勿
关闭应用；迁移完成后建议重启一次，让内核重新加载并校验身份。失败会自动回滚并
保留原管理器。

**Q：隐藏后如何恢复官方管理器？**
A：设置 → 工具 → 隐藏管理器 → 恢复官方管理器。恢复需要保存的原始 APK 可用且
签名有效；如果原始 APK 缺失或无效，页面会提示无法恢复。

**Q：隐藏状态下还能应用内更新吗？**
A：只能更新到**稳定版 Release 且 Release 带有 `SHA256SUMS`**：先做 size/SHA-256
强校验，再由隐藏管理器用保存的包名/图标/本地密钥重签名，内核识别与 root 保留。
nightly/测试版或没有校验和的更新会提示先恢复官方管理器。准备更新时保存的官方备份
就会刷新，因此取消安装提示后可以重新安装已准备的文件；手动绕过链路升级导致备份
更旧时，恢复会先装回较旧的官方版本再走官方更新。任何校验失败都会中止并显示对应
`hidden_manager_update_*` 错误。

**Q：隐藏管理器支持多用户吗？**
A：只支持 Android 主用户（user 0）。管理器运行在 secondary 用户下时隐藏/恢复会
直接拒绝；设备存在其他用户时界面会提示隐藏管理器只为 user 0 安装与恢复。

**Q：Material 3 和 Miuix 界面有什么区别？**
A：只是外观风格不同，功能与数据一致，可随时切换。选择会持久化（`ui_mode`），
默认 Material 3。

**Q：SUSFS 集成开关是什么？和模块里的 SUSFS 配置有何区别？**
A：设置 → 配置 → SUSFS 集成 是 NekoSU 对 SUSFS LKM 的加载开关：
- 打开：立即加载 SUSFS guard LKM，并持久化为开机自动加载
- 关闭：停止自动加载并尝试卸载
与「SUSFS 配置」页里的隐藏路径/kstat/AVC 等规则不同；如果内核已内置 SUSFS
（backend=builtin），开关会禁用并展示内置版本。状态由
`ksud susfs status --package-name` 提供，必要时提示重启。

**Q：AVC 日志欺骗是什么？为什么开关是灰的？**
A：把 SELinux 审计日志中的 root 域替换为 priv_app 域，降低被检测的概率。该功能
依赖内核支持：只有状态为 `supported` 时可切换；`forced`/`managed`/`unsupported`
时开关不可用。SUSFS 另有一个独立的 `avc_log_spoofing` 开关，默认关闭，需要手动开启。

**Q：为什么 AVC 开关默认是开着的？**
A：内核侧 `avc_spoof` 默认开启，管理器以实时值为准；只有在内核支持且状态为
`supported` 时才能改变它。

**Q：SUSFS 的 AVC opt-in 在哪里开？**
A：SUSFS 配置页的「AVC 日志欺骗」（`avc_log_spoofing`）默认关闭，需要手动开启；
它与内核 `avc_spoof` 是两个独立开关，互不替代。

**Q：KPM 是什么？现在能用吗？**
A：Kernel Patch Module（KPM）是内核补丁模块，页面在底部导航「KPM 入口」。当前是
**PoC**：内核侧 KPatch-Next 引擎尚未接入 NekoSU 内核，因此可以导入/查看/管理
`.kpm` 记录、开关集成，但**不能真正加载**。启用集成后同一次开机会提示重启；
重启后引擎仍不存在会显示「集成错误」，加载会返回 `engine_inactive`/`kernel_error`。

**Q：KPM 页面状态分别是什么意思？**
A：`正在读取` 表示查询中；`已集成（活动中）` 表示引擎可用；`集成已关闭` 表示
未启用；`等待重启` 表示集成开关已改需重启；`不可用` 表示设备/内核不支持；
`集成错误` 表示配置与活动状态不一致、重启后引擎仍未出现或发生转换失败。

**Q：KPM 怎么导入/加载？**
A：KPM 页「导入 .kpm」选择文件（≤ 32 MiB，按 SHA-256 内容寻址）；导入后在列表里
「加载」可选「仅加载一次」或「加载并自动载入」，「控制」需要该 KPM 提供控制接口，
「卸载」会在重启后生效。PoC 阶段这些操作会被后端以明确错误拒绝。

**Q：模块仓库是什么？可以自建吗？**
A：模块仓库使用 KernelSU Modules API v1（入口 `api/v1/index.json`，
schemaVersion=1）。内置源默认 `https://modules.kernelsu.org/`，构建时可用
`-PNEKOSU_REGISTRY_URL=https://...` 覆盖（空值禁用内置源）。也可以在「添加仓库」
里添加自建的 KernelSU Modules API 或 MMRL API 源（仅 https），URL 会持久化到本地。

**Q：registry 下载托管在哪里？为什么强校验 SHA-256？**
A：release ZIP 不限定托管域名：GitHub Releases、自建 registry 域名或 CDN 都可以，
只要 registry detail 的 `releases[].zip` 提供准确的 `size` 与 `sha256`。下载器只接受
https（重定向目标同样必须是 https），安装前强制流式校验 size + SHA-256，避免仓库
文档或镜像被篡改；`updateJson` 更新若来自 registry、与 registry 同源或位于 github，
也必须匹配 registry release 的 versionCode 与校验值。非 registry 的旧式 updateJson
保持原有路径。

**Q：如何校验下载的 APK？**
A：Release 附带 `SHA256SUMS`。在下载目录执行：
```bash
sha256sum -c SHA256SUMS --ignore-missing
```
或手动对比 `sha256sum NekoSU_*.apk` 与文件中的值；正式版还必须来自同一 release key。

**Q：如何反馈问题？**
A：GitHub Issues，附机型、内核、Android 版本与日志。
