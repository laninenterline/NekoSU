# 发布流程

> 发布定位：**临时 root（越狱模式）only**，发布说明中不得宣称永久 root / boot 刷写。

## 1. 版本号

- Tag：`vMAJOR.MINOR.PATCH`
- `versionName = git describe --tags --abbrev=0`
- `versionCode = 40000 + git rev-list --count HEAD`
  - 重写历史后仍保证高于旧版（旧公式为 `30000 + commitCount + 700`）
  - v1.0.1 = 40002

## 2. 发布步骤

1. 更新 `CHANGELOG.md`
2. 提交并推送 `main`
3. 打标签并推送：

   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

4. `release.yml` 自动执行：
   - `lkm` job：构建 8 个 GKI KMI 的 kernelsu + SUSFS ko
   - `apk` job：下载 ko、构建 Manager（release 签名）、打包 APK
   - 创建 GitHub Release，上传 APK
5. 发布后验证：

   ```bash
   apksigner verify --print-certs NekoSU_vX.Y.Z_<code>-arm64-v8a-release.apk
   ```

   证书 SHA-256：`e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0`

## 3. Release 资产

- APK 命名：`NekoSU_v<version>_<versionCode>-arm64-v8a-release.apk`
- 4.x ko 不单独上传，已内置于 `libksud.so`
- 不发布预发布版本；canary/临时 tag 不进入历史

## 4. 历史策略

- 只保留正式版本 tag 与对应 2 个历史提交（v1.0.0、v1.0.1）
- 预发布 tag / Release / 相关提交一律删除
- 历史重写后需 force-push，协作者重新 clone

## 5. 回滚

- 重新打 tag 指向旧正式提交并 force-push main（仅维护者）
- 或发布新的 patch 版本覆盖，不修改已发布 tag

## 6. 签名密钥

- release keystore 只存在于 CI Secrets
- 内核模块校验的证书与 Manager 发布证书一致
- 禁止在仓库、日志、Issue 中泄露 keystore/密码
