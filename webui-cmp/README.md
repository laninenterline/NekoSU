# webui-cmp（Compose Multiplatform WebUI）

与 Manager 共用同一套 Compose UI 代码的 Web 版本（Wasm），产物由 Manager 内置 HTTP 服务在 `/app` 提供。

## 构建

```bash
cd webui-cmp
./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon
# 产物: composeApp/build/dist/wasmJs/productionExecutable
```

Kotlin/Wasm 需要 Binaryen。若网络无法从 GitHub 下载，可手动下载后放到本地 Maven 仓库，并设置：

```bash
export NEKO_BINARYEN_MAVEN=/path/to/local-maven
```

仓库布局参考 `com/github/webassembly/binaryen/123/`。

## 数据

`Api.kt` 通过同源 `fetch` 调用 Manager 的 `/api/*`（token 由服务端注入 index.html 的 `window.NEKO_WEBUI`）。
静态预览下请求失败会自动回退到示例数据。

## 更新产物

构建后把 `productionExecutable/` 复制到 `manager/app/src/main/assets/webui-cmp/`，并保留带 `__TOKEN__` 占位符的 `index.html`。
