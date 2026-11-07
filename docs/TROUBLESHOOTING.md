# 故障排查

## 1. 日志位置

| 日志 | 位置 / 入口 |
|------|-------------|
| root 操作日志 | Manager → 日志中心 → SU |
| 越狱失败日志 | Manager → 日志中心 → jailbreak |
| 模块日志 | Manager → 日志中心 → metamodule |
| 内核日志 | `adb shell dmesg` / `su -c dmesg` |
| 内核模块加载 | `adb shell dmesg | grep -i kernelsu` |

## 2. 常见问题

### 2.1 late-load 失败

```bash
ksud late-load --allow-shell --package-name com.nekosu
# 观察输出
```

- 确认 KMI：`uname -r`
- 确认 ko 存在：`userspace/ksud/bin/aarch64/<KMI>_kernelsu.ko`
- 确认当前是临时 root 环境（越狱提权成功）
- 4.x 检查 SUSFS ko 是否与 kernelsu.ko 同 KMI

### 2.2 管理器检测不到 root

- 临时 root 是否仍然有效（未重启）
- 尝试重新越狱，或重启 Manager
- 检查 `allow_shell` / 包名是否匹配

### 2.3 KMI 识别失败

- 使用 `--kmi` 指定
- 检查 `/vendor/lib/modules` 是否存在可读 ko
- Manager → KMI 诊断页

### 2.4 4.x 内核问题

- 收集：`uname -a`、KMI、`dmesg`、`lsmod`、SUSFS ko 版本
- 确认设备 Android 版本 ≥ 8（否则无法安装 Manager）
- 4.x 为实验性，问题请附完整日志

## 3. 提交 Issue 模板

```text
设备 / ROM：
Android 版本：
内核版本（uname -a）：
KMI（自动识别 / 手动指定）：
临时 root 方式（Magica / 手动）：
复现步骤：
期望行为 / 实际行为：
日志（dmesg、日志中心导出、越狱失败日志）：
```

## 4. 安全提示

- 不要在 Issue 中粘贴 keystore、密码、私人路径
- 越狱失败导致的 reboot loop：进入安全模式或恢复模式，清理相关属性/脚本
