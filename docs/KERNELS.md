# 内核支持

> 本项目**只提供临时 root（越狱模式）**：所有内核的 ko 都通过用户态 late-load 加载，
> 不刷写 boot 镜像，重启后失效。

## 支持矩阵

| 内核系列 | KMI | kernelsu.ko | SUSFS LKM | 状态 |
|---|---|---|---|---|
| 4.19 | android10-4.19 | ✅ | ✅ | 实验性 |
| 4.14 | android9-4.14 | ✅ | ✅ | 实验性 |
| 4.9 | android8-4.9 | ✅ | ✅ | 实验性 |
| 4.4 | android7-4.4 | ✅ | ✅ | 实验性 |
| 5.4 | android11-5.4 | ✅ | ✅ | 稳定 |
| 5.10 | android12-5.10 / 13-5.10 | ✅ | ✅ | 稳定 |
| 5.15 | android13-5.15 / 14-5.15 | ✅ | ✅ | 稳定 |
| 6.1 | android14-6.1 | ✅ | ✅ | 稳定 |
| 6.6 | android15-6.6 | ✅ | ✅ | 稳定 |
| 6.12 | android16-6.12 | ✅ | ✅ | 稳定 |
| 6.18 | android17-6.18 | ✅ | ✅ | 稳定 |

## 4.x 说明

- 4.x 没有 GKI/DDK，ko 为 CI 交叉编译后入库的预编译产物
- 4.14 / 4.9 / 4.4 的 `security_hook_list` 为 `struct list_head`，
  走 `kernel/susfs/lsm_hook_old.c`；4.19 及 5.x 走 hlist 实现
- 4.x 无 BTF：SELinux sid 偏移、fsnotify 生命周期等 BTF 特性降级
- Manager minSdk = 26（Android 8+），4.4（Android 7）设备只能通过 ksud late-load
- 预编译 ko 尚未真机验证，遇到问题请附 `dmesg` 与内核版本提 Issue
