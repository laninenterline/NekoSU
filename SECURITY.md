# Security Policy

## 支持版本

| 版本 | 支持 |
|------|------|
| v1.0.1 | ✅ |
| v1.0.0 | 历史版本 |

## 报告漏洞

请**不要**公开提交 Issue。通过 GitHub Security Advisories 或私下联系维护者，
提供：

- 影响版本 / 内核 KMI
- 复现步骤与 PoC（如可提供）
- 影响评估（本地提权 / root 逃逸 / 信息泄露等）

我们会尽快确认并在修复后公开致谢。

## 安全模型

NekoSU 只提供**临时 root（越狱模式）**：

- 不修改 boot / 系统分区，不处理 AVB
- 内核模块只在临时 root 期间存在，重启即卸载
- `allow_shell` 控制 adb shell 的 root 暴露面
- App Profile 可限制包名的 root、SELinux 域与 seccomp
- 越狱入口（Magica / 手动）依赖设备本身的提权能力，其风险由用户承担

## 发布签名

- APK 使用固定 release keystore
- 证书 SHA-256：`e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0`
- 内核模块校验的证书与 Manager 发布证书一致
- 如发现签名不一致的 APK，请视为非官方版本并报告

## 不泄露

仓库、日志、Issue 中禁止出现 keystore、密码、token、私人路径等敏感信息。
