# 发布流程

## 版本号

- 版本来自 tag：`vMAJOR.MINOR.PATCH`
- `versionName = git describe --tags --abbrev=0`
- `versionCode = 40000 + git rev-list --count HEAD`（重写历史后仍保证单调递增）

## 发布步骤

1. 更新 `CHANGELOG.md`
2. 打 tag 并推送：`git tag vX.Y.Z && git push origin vX.Y.Z`
3. `release.yml` 自动：构建 8 个 GKI 的 ko + Manager（release 签名）→ 创建 GitHub Release
4. 发布后校验 APK 签名证书 SHA-256：

   `e6b6840253ef5c04252ccd3a0e7b3d832dbe59730b8f1d895b5d3e89ac9d84d0`

## 历史策略

- 仅保留正式版本 tag；预发布 tag 与提交不进入历史
- 历史重写后需要 force-push，协作者需重新 clone
