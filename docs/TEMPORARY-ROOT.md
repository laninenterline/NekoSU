# 临时 root（越狱模式）说明

## 是什么

NekoSU 的 root 是**临时的**：内核模块（`kernelsu.ko`）由 `ksud` 在系统启动后从
用户态 `late-load` 进内核，配合 `susfs_guard_lkm.ko` 提供隐藏能力。

- **不刷写 boot 镜像**，不修改 `boot` / `init_boot` / `vendor_boot`
- **不做 AVB / dm-verity 处理**，不改系统分区
- **重启后失效**：内核模块卸载，需要重新越狱并 late-load

## 越狱模式（jailbreak）

1. 设备先通过越狱入口取得临时提权（如 Magica 一键越狱，或厂商/通用提权方式）
2. 管理器或 `ksud` 执行 late-load，把内核模块加载进当前内核
3. 管理器检测到 root 后提供模块、SU、日志等功能

手动 late-load：

```bash
ksud late-load --allow-shell --package-name com.nekosu
```

## 不提供什么

| 能力 | 是否支持 |
|---|---|
| 刷写 / patch boot 镜像 | ❌ |
| 永久 root | ❌ |
| 永久卸载（恢复原厂 boot） | ❌ |
| 模块刷写（临时生效） | ✅ |
| SUSFS 隐藏（临时 root 期间） | ✅ |

## 为什么

临时 root 方案不触碰启动链，风险低、不需要解锁或重新刷机；代价是每次重启都要重新越狱。
如果未来加入永久 root，会单独设计 boot patch 与卸载流程，并更新本页。
