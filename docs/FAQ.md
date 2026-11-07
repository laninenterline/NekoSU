# 常见问题

## 安装与使用

**Q：这是永久 root 吗？**
A：不是。本项目只提供临时 root（越狱模式），不刷写 boot 镜像，重启后 root 失效。

**Q：怎么获得 root？**
A：用一键越狱（Magica）或自行取得临时提权后执行
`ksud late-load --allow-shell --package-name com.nekosu`。

**Q：重启后管理器显示未安装？**
A：临时 root 的正常状态，重新越狱即可。

**Q：支持 Android 7 / 内核 4.4 吗？**
A：4.4 的 LKM 已支持，但 Manager minSdk=26（Android 8+）无法安装；
只能通过 `ksud` 手动 late-load。

**Q：支持哪些内核？**
A：GKI 5.4 – 6.18，以及 4.19 / 4.14 / 4.9 / 4.4（实验性）。见 [内核支持](KERNELS.md)。

## 越狱

**Q：一键越狱后设备重启了？**
A：部分 OEM ROM 修改 adbd 属性会触发重启；管理器有重启保护，重启后自动禁用一键越狱，
可在日志中心查看 jailbreak 失败日志，之后用手动 late-load。

**Q：`--allow-shell` 是什么？**
A：加载内核模块时传 `allow_shell=1`，允许 adb shell 直接获得 root；不需要可去掉。

**Q：越狱失败怎么排查？**
A：日志中心 → jailbreak 分类；或 `adb shell dmesg | grep -i kernelsu`。

## 内核与 KMI

**Q：KMI 识别错误/无法识别？**
A：用 `ksud late-load --kmi <KMI>` 强制指定；或查看 KMI 诊断页。

**Q：4.x 内核有哪些限制？**
A：BTF 相关特性降级、SUSFS 部分能力实验性、未真机验证；详见 [内核支持](KERNELS.md)。

## 开发

**Q：如何本地构建？**
A：见 [构建指南](BUILDING.md)。

**Q：为什么提交历史只有两个 commit？**
A：项目只保留正式版本历史，预发布与开发提交已清理；见 [发布流程](RELEASING.md)。
