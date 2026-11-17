
# 临时 root（越狱模式）

NekoSU **只支持临时 root**：不刷 boot、不写内核镜像，root 只在当前开机周期有效。

## 原理

1. 管理器启动 Magica 服务，触发 app zygote 预加载
2. 在 zygote 子进程中执行 `ksud`，获得受限 root
3. `ksud` 临时开启 adbd root，并通过 adb shell 执行 `ksud late-load`
4. `late-load` 加载 `kernelsu.ko` 与 `susfs_guard_lkm.ko`，恢复 SELinux、初始化模块
5. 恢复 adbd 属性；管理器随后通过 `su` 使用 root

## 操作

- 首页 →「越狱」，按提示确认
- 或手动：`su -c "ksud late-load"`（需要先有 adb root 环境）

## 限制

- **重启后失效**，需要重新越狱
- 部分厂商 ROM 对 adbd 属性修改敏感，可能触发重启或安全告警
- 越狱失败会记录到日志中心 → Magica
- 需要设备为可调试 / 可获取临时 root 的环境

## 与管理器身份的关系

内核不会只凭包名信任管理器，还会校验 `manager_identity`（包名 + 证书 SHA-256）。
因此：

- 换签名的安装包、卸载重装或手动改包名后，需要重新完成一次越狱 / late-load，
  让内核重新读取管理器身份
- 使用「隐藏管理器」迁移后，建议重启一次再越狱，确认内核已重新加载并校验新身份
- root 只在当前开机周期有效，但 allowlist / 模块 / Profile 数据保存在
  `/data/adb`，重启后仍然存在

## 隐藏管理器

设置 → 工具 → 隐藏管理器 可以把管理器替换为随机包名、本地自签名证书的副本，
用于降低管理器本身被识别的概率。注意：

- 迁移使用一次性 token 与交接记录，新副本确认内核可以管理后才卸载原管理器；
  任一步失败会自动回滚，原管理器保留
- 原始 APK 保存在管理器私有目录，可在同一页面恢复官方管理器
- 迁移期间不要关闭应用；迁移完成后建议重启，让内核重新加载并校验身份
- 隐藏状态下的更新只接受稳定版 Release + `SHA256SUMS`：先校验 size/SHA-256，再
  用保存的包名/图标/本地密钥重签名；nightly/无校验和的更新会要求先恢复官方管理器
- 准备更新时保存的官方备份 APK 会立即刷新（取消安装后仍可重试安装准备文件）；
  手动升级导致备份更旧时，恢复会先安装较旧官方版本再走官方更新
- 隐藏/恢复只支持 Android user 0；secondary 用户会被拒绝
- 因为 versionCode 来自 `version.properties` 且必须递增，旧版本更新会被拒绝
- 如果原始 APK 缺失或无效，恢复会失败并在页面提示

## 更新与 root

- 正式版 APK 使用固定签名，应用内更新原地覆盖，**root 保留**
- 卸载重装、换签名安装包会清除应用数据与 allowlist，root 需要重新越狱
- Release 附带 `SHA256SUMS`，管理器下载更新后会强制校验 SHA-256

## KPM 与 root

KPM（Kernel Patch Module）管理面需要 root 才能调用 `ksud kpm ...`，但当前是 PoC：
内核侧 KPatch-Next 引擎尚未接入，集成开关只能持久化，加载会返回
`engine_inactive`/`kernel_error`；x86_64 返回 `unsupported`。真实加载需要内核侧
引擎支持（Android ARM64）。

## 模块 WebUI 与 root

模块 WebUI 运行在管理器进程内，文件操作与命令执行使用 root shell；因此 WebUI
等同于模块的 root 能力入口。只打开可信模块的 WebUI，并注意其下载/外链行为。
