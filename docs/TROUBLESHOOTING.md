
# 故障排查

## 越狱失败

1. 查看「日志中心 → Magica」中的记录
2. 确认设备可调试、未被厂商限制 adbd 属性修改
3. 尝试手动 `su -c "ksud late-load"`
4. 若触发重启，重新打开应用会提示并禁用一键越狱

## 首页显示「未获取临时 root」

- 正常现象：重启后需要重新越狱
- 若刚越狱仍显示异常：检查 KMI 诊断页的加载状态与 KMI 匹配

## 模块不生效

- 确认模块已启用
- 检查模块要求的 KMI 与内核版本
- 查看日志中心 → Problem / Metamodule

## KO 加载失败

- GKI：确认 KMI 与内置 assets 匹配
- 5.4：确认已导入自定义 `kernelsu.ko`
- 4.x：确认使用仓库内置的对应版本 KO

## 管理更新失败

- 只接受同签名正式版 APK
- 更新后 root 保留；若卸载重装需重新越狱

## KPM 无法加载

- 状态「等待重启」→ 重启设备后再试
- 重启后仍显示「集成错误」/加载返回 `engine_inactive`、`kernel_error`：当前 PoC 的
  预期行为（内核未接入 KPatch-Next 引擎）
- x86_64 设备返回 `unsupported`：KPM 传输层仅 Android ARM64
- 确认导入文件是 `.kpm` 且 ≤ 32 MiB、非空

## 模块仓库刷新/下载失败

- 自建源必须是 https，并实现 KernelSU Modules API v1（`schemaVersion=1`）；协议不匹配
  会提示升级或格式错误
- 网络失败时会回退最后一份缓存；仍失败可删除应用缓存或稍后重试
- registry 下载只接受 https（GitHub Releases、自建域名或 CDN 均可），并按 registry
  文档中的 size + SHA-256 强制校验；被阻止或校验失败时不会安装
  （见 `repo_download_url_blocked` / `repo_download_verify_failed`）

## SUSFS 集成/AVC

- 设置 → 配置 → SUSFS 集成：`builtin` 表示内核已内置（开关禁用）；`rebootRequired`
  提示重启；`autoload` 但未 loaded 属等待加载
- AVC 内核特性仅在 `supported` 时可切换；SUSFS 的 `avc_log_spoofing` 默认关闭，
  需在 SUSFS 配置页手动 opt-in

## 获取日志

- 设置 → 工具 → 日志中心 → 选择来源 → 导出
- 提 Issue 时请附：机型、Android 版本、内核版本、KMI、日志
