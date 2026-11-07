# KernelSU 改版功能对比 & NekoSU 差距分析

> 调研日期：2026-10-31
> 对比对象：KernelSU 官方、KernelSU-Next、SukiSU-Ultra、ReSukiSU、FinaSU、KowSU、BakaSU（本项目早期基座）

## 一、调研来源

| 改版 | 仓库 / 文档 |
|---|---|
| KernelSU | https://github.com/tiann/KernelSU |
| KernelSU-Next | https://github.com/KernelSU-Next/KernelSU-Next |
| SukiSU-Ultra | https://github.com/SukiSU-Ultra/SukiSU-Ultra · https://sukisu.org |
| ReSukiSU | https://github.com/ESK-Project/ReSukiSU |
| FinaSU | https://github.com/LyraVoid/FinaSU |
| KowSU | https://github.com/KOWX712/KernelSU |
| BakaSU | https://github.com/Baka-SU/BakaSU |
| WildKernels 特性索引 | https://github.com/wildkernels/gki_kernelsu_susfs/blob/main/docs/features.md |

## 二、能力矩阵

图例：✅ 有 / 🟡 部分或需扩展模块 / ❌ 无

| 能力 | KernelSU | KSU-Next | SukiSU-Ultra | ReSukiSU | FinaSU | NekoSU(我们) |
|---|---|---|---|---|---|---|
| 内核 su / 权限管理 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| App Profile | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Metamodule 模块体系 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| LKM / KMI 加载 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅（单 APK 内 4 KMI） |
| SUSFS | ❌（外置补丁） | 🟡（集成构建） | ✅ 内置 | ✅ 内置 | ✅ | ✅（LKM guard） |
| 多管理器支持 | ❌ | ❌ | 🟡 | ✅ | ❌ | ✅ |
| KPM（KernelPatch Module） | ❌ | ❌ | ✅ | 🟡 | ❌ | ❌（BakaSU 已移除） |
| 内置 Zygisk 引擎 | ❌ | ❌ | ❌ | ❌ | ✅（FolkZygisk） | ❌（仅外部 ZygiskNext） |
| Sui / Shizuku API 内置 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| 内置挂载引擎（Magic Mount/OverlayFS/Folk-Mount） | ❌ | ✅ | ❌ | ❌ | ✅ | ❌（仅 metamodule） |
| 模块 WebUI | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 模块在线仓库 / 一键安装 | ❌ | ❌ | 🟡 | ❌ | ✅ | ✅（moduleRepo） |
| 内核模块黑名单（boot 阻止加载） | ✅（开发者项） | ❌ | ✅（kernelmodules 页） | ❌ | ✅ | 🟡 内核已有，管理器无 UI |
| 主题商店 / 字体 / 壁纸 / BannerAPI | ❌ | ❌ | 🟡（主题） | 🟡 | ✅ | ❌（仅主题/背景/图标开关） |
| x86_64 / riscv64 | ✅ x86 | ✅ x86 | ✅ 部分 x86 | ✅ x86 | 🟡（hook 在） | ❌（仅 arm64） |
| 非 GKI 旧内核（4.14 / 3.x） | 🟡 手动编 | ✅ 4.4+ | ✅ 3.4+ | ✅ 3.4+ | 🟡 | ❌（仅 GKI 2.0） |
| 一键越狱 / Magica | 🟡 | ❌ | ❌ | ❌ | ❌ | ✅（兼容保护 + 失败日志） |
| 失败日志单独记录 / 导出 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 运行期 vermagic 适配（同一 ko 跨内核） | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

## 三、我们缺失的功能（按优先级）

### P0：低成本、能直接补短板

1. **内核模块黑名单 UI**
   - 我们内核已有 `kernel/feature/module_blacklist.c`（`block_modules` 参数，默认 `vr,vklp,oplus_secure_guard,...`），但管理器没有入口/页面
   - 对照：KernelSU 开发者项、SukiSU `kernelmodules` 页、FinaSU `kernelmodule` 页
   - 工作量：1 天内（加设置页 + ksu_config 读写）

2. **应用图标切换独立页**
   - 我们已有 alternate icon 开关，SukiSU 有完整 `appicon` 页面（多图标、预览）
   - 工作量：1 天内

3. **模块仓库对齐**
   - 我们已有 `moduleRepo`（在线源 + 详情），可补一键安装/来源管理/更新提示，对齐 FinaSU
   - 工作量：1–2 天

### P1：中等工作量、提升竞争力

4. **KPM（KernelPatch Module）支持**
   - SukiSU-Ultra 有完整实现：`kernel/extension/kpm/*` + ksud `kpm_cmd`（load/unload/list）+ 管理器 KPM 页面
   - 我们管理器里甚至保留注释「Drop KPM support」——BakaSU 早期主动砍掉
   - 工作量：数天～1 周（内核 extension 框架 + ksud + UI，且与 LKM/late-load 的加载顺序要处理）

5. **扩展框架（extensions / CPU spoof 等）**
   - SukiSU 的 `extensions` 页面统一管理 kpm/cpu_spoof/umount_paths/dynamic_manager 等扩展能力
   - 我们目前只有 dynamic_manager / umount 等零散入口
   - 工作量：3–5 天（依赖 KPM/extension 框架）

6. **独立安装/刷写页（install）**
   - SukiSU/FinaSU 有 boot 镜像安装页；我们只有 LKM 临时 root + Flash 页
   - 若坚持“只做 LKM 临时 root”，此项可跳过

### P2：大工程或产品策略取舍

7. **内置 Zygisk 引擎（FolkZygisk）** — FinaSU 独有，需引入 zygisk 注入引擎（大工程）
8. **Sui / Shizuku API 内置** — FinaSU 独有，需嵌入 Sui 载荷 + 管理通道
9. **内置挂载引擎（Folk-Mount / Magic Mount / OverlayFS）** — KSU-Next、FinaSU 有，我们目前依赖 metamodule
10. **x86_64 / riscv64 LKM + 管理器** — BakaSU 曾有 x64 LKM，我们主动只编 arm64；若要做模拟器/WSA 需恢复
11. **非 GKI 旧内核（4.14 / 3.x）** — SukiSU/ReSukiSU 支持；我们仅 GKI 2.0
12. **主题商店/字体/壁纸/BannerAPI/UTS/NetIsolate/插件系统** — FinaSU 生态扩展，锦上添花

## 四、我们的独有优势（保持）

- 单 APK 内置 4 个 KMI 的 kernelsu.ko + SUSFS guard ko
- 运行期 vermagic 适配（同一镜像跨内核/OTA）
- Magica 一键越狱 + 兼容性预检 + 重启保护 + 失败日志单独记录/导出
- 多管理器支持（官方/GKI 系管理器可共存）
- 动态管理器、SuSFS 管理页、bugreport、完整中文界面

## 五、建议路线图

1. **短期（1 周内）**：P0 三项（内核模块黑名单 UI、图标页、模块仓库增强）
2. **中期（2–4 周）**：KPM + extensions 框架；同时评估是否引入内置挂载引擎
3. **长期（视产品定位）**：x86_64/riscv64、非 GKI 支持、Zygisk/Sui 集成
