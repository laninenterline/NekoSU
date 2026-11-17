
# 上游同步策略

## 上游

- KernelSU：`https://github.com/tiann/KernelSU`
- SUSFS：`https://gitlab.com/simonpunk/susfs4ksu`

## 同步原则

1. 不改变 NekoSU 的临时 root 定位
2. 优先同步安全修复与内核接口变更
3. 新特性需评估是否依赖永久 root / boot 镜像
4. 每次同步后跑完整 CI（manager + ksud + LKM）

## 本地增量

- `kernel/susfs`：SUSFS LKM 化与 4.x 兼容
- `userspace/ksud`：late-load、Magica 引导、KMI 匹配、日志
- `manager`：临时 root UI、诊断、日志中心、主题
- `uapi`：supercall 命令扩展

## 记录

重大同步需在 PR 中说明：上游 commit、改动范围、验证机型与内核。
