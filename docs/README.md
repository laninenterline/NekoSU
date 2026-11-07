# NekoSU 文档

NekoSU 是 KernelSU 的临时 root（越狱模式）分支，包名 `com.nekosu`。
本目录是整个项目的技术文档入口。

| 文档 | 面向 | 内容 |
|------|------|------|
| [架构说明](ARCHITECTURE.md) | 开发者 | 组件划分、启动链、KMI 选择、模块系统、日志、安全模型 |
| [临时 root 说明](TEMPORARY-ROOT.md) | 用户 / 开发者 | 越狱模式定义、late-load 流程、与永久 root 的区别 |
| [构建指南](BUILDING.md) | 开发者 | ksud、Manager、5.x/4.x LKM 的本地与 CI 构建 |
| [内核支持](KERNELS.md) | 用户 / 开发者 | KMI 矩阵、4.x 移植细节、限制与验证方法 |
| [发布流程](RELEASING.md) | 维护者 | 版本号、签名、Tag 与 Release 自动化 |
| [常见问题](FAQ.md) | 用户 | 安装、临时 root、4.x、管理器兼容性 |
| [故障排查](TROUBLESHOOTING.md) | 用户 / 维护者 | 日志位置、常见错误、Issue 模板 |
| [WebUI](WEBUI.md) | 用户 / 开发者 | 浏览器版管理器、API、安全与限制 |
| [参考](reference/) | 开发者 | 上游同步记录、功能差异分析 |

> 项目定位：**只提供临时 root（越狱模式）**，不刷写 boot 镜像、不做 boot patch、
> 不提供永久 root 或永久卸载。重启后 root 失效，需要重新越狱并 `ksud late-load`。
