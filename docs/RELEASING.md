
# 发布流程

NekoSU 采用 **tag 触发**的发布方式，正式版只通过 GitHub Actions 构建。
本文件描述发布门禁与版本契约；具体步骤以 `.github/workflows/release.yml` 为准
（release workflow 与本轮功能同步演进，若两者不一致，先修 workflow 再发布）。

## 发布前检查

1. `main` 通过 `Quality`（lint + JVM 单元测试 + 「确认测试真的执行」门禁）与
   `Build Manager (debug)`
2. **递增 `version.properties` 的 `NEKOSU_VERSION_CODE`**，并确认
   `NEKOSU_VERSION_NAME` 与本次 tag 一致（例如 tag `v1.0.2` → NAME `1.0.2`）
3. 所有面向用户的改动已同步 README / `docs/` / CHANGELOG
4. 新增字符串已补 i18n，`values/strings.xml` 中可翻译项在主要语言中可用

## 版本契约

`version.properties` 是**唯一**版本来源：

```properties
NEKOSU_VERSION_NAME=1.0.0
NEKOSU_VERSION_CODE=40002
```

规则：

- Gradle 直接读取 `version.properties` 生成 `versionName` / `versionCode`；
  文件缺失时才回退旧的 git 推导逻辑（`40000 + 提交数` / `git describe`）
- `NEKOSU_VERSION_CODE` 必须在**每次发布**时递增。管理器更新、隐藏态更新、
  灰度/回滚都依赖 versionCode 单调递增；单提交/压缩历史仓库也不会因此卡住
- `NEKOSU_VERSION_NAME` 必须与发布 tag 一致；`tools/verify-release.py` 容忍
  APK 侧的前导 `v`（Gradle 从 tag 派生时会带 `v`）
- 发布时先改 `version.properties`、提交，再打 tag 构建，保证 APK 内版本与
  Release 一致
- 不要通过改 Gradle 推导逻辑“修版本号”，也不要手工改 APK

## 构建

1. 更新 `version.properties`（CODE +1，NAME 与 tag 一致）并提交
2. 打 tag 并推送：
   ```bash
   git tag -a v1.0.1 -m "NekoSU v1.0.1"
   git push origin main refs/tags/v1.0.1
   ```
3. `Release` 工作流自动：
   - 构建 8 个 KMI 的 arm64 `kernelsu.ko` + `susfs_guard_lkm.ko`
     （`build-lkm.yml`）
   - best-effort 构建 8 个 KMI 的 x86_64 `kernelsu.ko`（DDK x86_64；失败不阻塞
     arm64 发布）；上游 SUSFS LKM 无 x86_64 版本
   - best-effort 构建 arm64/x86_64 `ksuinit` 并放入 `userspace/ksud/bin/<abi>/`
   - 按 ABI 构建 ksud（arm64-v8a / x86_64）并 staging 到 `jniLibs`（嵌入上述资产）
   - 构建正式签名 APK（通用包 + 分 ABI 包）
   - 校验签名、版本契约、ABI 机器类型、native 库与 ksud 构建标识
   - 审计公开源码树（`tools/audit-public-source.py`）
   - 生成 `SHA256SUMS`（只覆盖实际产出的 APK）并随 Release 上传
   - 创建/更新 GitHub Release 并上传 APK 与 SHA256SUMS；显式 `--draft=false`，
     已有 draft 会 `gh release edit --draft=false` 转为正式发布；校验报告作为
     workflow artifact（`release-verification`）保存

## 发布门禁（必须全部通过）

| 门禁 | 工具 | 内容 |
|---|---|---|
| 公开源码审计 | `tools/audit-public-source.py` | 禁止私钥、签名文件、本地构建产物、设备私有信息进入公开树 |
| APK 校验 | `tools/verify-release.py` | applicationId、版本契约、签名/证书、ABI 集合与机器类型、native 库、ksud 载荷、非 debuggable/testOnly |
| 单元测试 | `:app:testDebugUnitTest` | JVM 测试（隐藏管理器身份/更新、下载、registry 解析、AVC/UiMode 契约等）；Quality 会校验测试结果文件确实生成 |
| Lint | `:app:lintDebug` | Android lint |

任一门禁失败都不得发布。

## SHA256SUMS

Release 附带 `SHA256SUMS`，覆盖通用包与分 ABI 包；管理器应用内更新会读取该资产
并强制校验 SHA-256（Release 存在 SHA256SUMS 但缺少目标 APK 条目时拒绝更新）。
隐藏管理器更新同样先做 SHA-256 校验，再重签名。

生成方式（workflow 内；只对实际生成的 APK 求校验和，缺失的分 ABI/通用包会被跳过，
不会因为“固定三个资产”而失败）：

```bash
cd manager/app/build/outputs/apk/release
shopt -s nullglob
APKS=( *arm64-v8a-release.apk *x86_64-release.apk *armeabi-v7a-release.apk *universal-release.apk )
shopt -u nullglob
test "${#APKS[@]}" -gt 0
sha256sum "${APKS[@]}" > SHA256SUMS
```

下载后校验：

```bash
sha256sum -c SHA256SUMS --ignore-missing
# 或手动对比
sha256sum NekoSU_*.apk
```

## 多 ABI 发布

- Release 同时发布 `arm64-v8a`、`x86_64` 分 ABI 包与 `universal` 通用包
- 分 ABI 包必须包含对应 `libksud.so`；`verify-release.py` 会按 ABI 校验 native
  库机器类型（AArch64 / x86_64）
- x86_64 由 DDK 构建 `kernelsu.ko`（best-effort）并构建 `ksuinit`，可用
  `ksud late-load`；上游 SUSFS LKM 只有 arm64，boot image capsule 补丁与 KPM
  传输层也仅 ARM64（详见 [架构](ARCHITECTURE.md) 的「多 ABI」）
- 发布允许部分资产：`lkm-x86_64` 与 `ksuinit` 构建失败时，Release 仍会发布
  arm64 产物；`SHA256SUMS` 仍然只列出实际存在的 APK
- 管理器按设备 `SUPPORTED_ABIS` 选择更新包，通用包兜底

## 签名

所有正式版使用同一把 release key，保证覆盖安装与 root 保留。
debug 版本使用独立签名，不能覆盖正式版。

隐藏管理器安装的副本使用**本地生成的密钥**；隐藏状态下的更新会被重签名回隐藏
身份，因此发布签名校验仍以官方 APK 为准，不受隐藏副本影响。隐藏态更新只接受
稳定版 Release 且必须带 `SHA256SUMS`；nightly/测试版不提供给隐藏副本（需先恢复
官方管理器）。

## 回滚

- 保留旧 APK 即可回退；同签名覆盖安装（versionCode 必须更高，回滚需发布新的更高
  versionCode）
- 跨签名回退需要卸载，会丢失临时 root 状态
- 隐藏管理器回退官方管理器使用设置页的「恢复官方管理器」，不要直接卸载隐藏副本
