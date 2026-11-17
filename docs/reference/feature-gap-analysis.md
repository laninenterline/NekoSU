
# 功能范围与上游对比

NekoSU 基于 KernelSU 与 SUSFS，面向**临时 root（越狱模式）**场景。
本文记录相对上游的增量与取舍，作为后续同步的基线。

## 相对 KernelSU 的增量

| 能力 | 说明 |
|---|---|
| 临时 root | Magica 引导 + `ksud late-load`，重启失效，不刷 boot |
| SUSFS LKM | 以可加载模块形式提供 SUSFS，按 KMI 打包 |
| 4.x 支持 | 4.19 / 4.14 / 4.9 / 4.4 预编译 KO |
| 5.4 自定义 KO | 无统一 GKI，支持用户导入 `kernelsu.ko` |
| KMI 诊断 | 版本推断、匹配状态、加载方式、建议 |
| Magica 兼容 | 高风险厂商提示、失败记录、重启检测 |
| 日志中心 | SU / Magica / Crash / Metamodule / Problem |
| 主题 | Material 3、动态取色、卡片透明度、背景 |

## 明确不做

- 永久 root / boot 刷写
- 开机自启 root
- 绕过应用检测的通用方案（SUSFS 仅提供有限隐藏）
- 在线服务依赖（更新检查除外）

## 取舍说明

临时 root 意味着所有能力必须能在一次越狱 + late-load 后建立；
因此内核侧与 ksud 的初始化流程（模块脚本、SELinux、profile）都围绕该约束设计。
