> 本文说明 NekoSU 当前实现的模块 WebUI JS API；`exec` / `spawn` / UI / 旧版包接口沿用上游
> KernelSU 文档，`io` / `pm` / 下载相关章节为 NekoSU 实际行为（含 Android 版本差异）。

# Library for KernelSU's module WebUI

## Install

```sh
yarn add kernelsu
```

## API

### exec

Spawns a **root** shell and runs a command within that shell, returning a Promise that resolves with the `stdout` and `stderr` outputs upon completion.

- `command` `<string>` The command to run, with space-separated arguments.
- `options` `<Object>`
  - `cwd` - Current working directory of the child process.
  - `env` - Environment key-value pairs.

```javascript
import { exec } from 'kernelsu';

const { errno, stdout, stderr } = await exec('ls -l', { cwd: '/tmp' });
if (errno === 0) {
    // success
    console.log(stdout);
}
```

### spawn

Spawns a new process using the given `command` in **root** shell, with command-line arguments in `args`. If omitted, `args` defaults to an empty array.

Returns a `ChildProcess` instance. Instances of `ChildProcess` represent spawned child processes.

- `command` `<string>` The command to run.
- `args` `<string[]>` List of string arguments.
- `options` `<Object>`:
  - `cwd` `<string>` - Current working directory of the child process.
  - `env` `<Object>` - Environment key-value pairs.

Example of running `ls -lh /data`, capturing `stdout`, `stderr`, and the exit code:

```javascript
import { spawn } from 'kernelsu';

const ls = spawn('ls', ['-lh', '/data']);

ls.stdout.on('data', (data) => {
  console.log(`stdout: ${data}`);
});

ls.stderr.on('data', (data) => {
  console.log(`stderr: ${data}`);
});

ls.on('exit', (code) => {
  console.log(`child process exited with code ${code}`);
});
```

#### ChildProcess

##### Event 'exit'

- `code` `<number>` The exit code if the child process exited on its own.

The `'exit'` event is emitted when the child process ends. If the process exits, `code` contains the final exit code; otherwise, it is null.

##### Event 'error'

- `err` `<Error>` The error.

The `'error'` event is emitted whenever:

- The process could not be spawned.
- The process could not be killed.

##### `stdout`

A `Readable Stream` that represents the child process's `stdout`.

```javascript
const subprocess = spawn('ls');

subprocess.stdout.on('data', (data) => {
  console.log(`Received chunk ${data}`);
});
```

#### `stderr`

A `Readable Stream` that represents the child process's `stderr`.

### fullScreen

Request the WebView enter/exit full screen.

```javascript
import { fullScreen } from 'kernelsu';
fullScreen(true);
```

### enableEdgeToEdge

Request the WebView to set padding to 0 or safeDrawing insets

- tips: this is disabled by default but if you request resource from `internal/insets.css`, this will be enabled automatically.
- To get insets value and enable this automatically, you can
  - add `@import "https://mui.kernelsu.org/internal/insets.css";` in css OR
  - add `<link rel="stylesheet" type="text/css" href="/internal/insets.css" />` in html.

```javascript
import { enableEdgeToEdge } from 'kernelsu';
enableEdgeToEdge(true);
```

### toast

Show a toast message.

```javascript
import { toast } from 'kernelsu';
toast('Hello, world!');
```

### moduleInfo

Get module info.

```javascript
import { moduleInfo } from 'kernelsu';
// print moduleId in console
console.log(moduleInfo());
```

### listPackages

List installed packages.

Returns an array of package names.

- `type` `<string>` The type of packages to list: "user", "system", or "all".

```javascript
import { listPackages } from 'kernelsu';
// list user packages
const packages = listPackages("user");
```

- tips: when `listPackages` api is available, you can use ksu://icon/{packageName} to get app icon.

``` javascript
img.src = "ksu://icon/" + packageName;
```

### getPackagesInfo

Get information for a list of packages.

Returns an array of `PackagesInfo` objects.

- `packages` `<string[]>` The list of package names.

```javascript
import { getPackagesInfo } from 'kernelsu';
const packages = getPackagesInfo(['com.android.settings', 'com.android.shell']);
```

#### PackagesInfo

An object contains:

- `packageName` `<string>` Package name of the application.
- `versionName` `<string>` Version of the application.
- `versionCode` `<number>` Version code of the application.
- `appLabel` `<string>` Display name of the application.
- `isSystem` `<boolean>` Whether the application is a system app.
- `uid` `<number>` UID of the application.

### exit

Exit the current WebUI activity.

```javascript
import { exit } from 'kernelsu';
exit();
```

## io（文件 API）

`io` 直接映射到 Manager 注册的 `ksu.io()` JavaScript 接口（`ksu.io().version() === 1`）。
每个模块 WebUI Activity 都有**独立的 ksu.io 会话**：各自的 root shell、各自打开的流/文件计数，
因此堆叠打开多个模块 WebUI 时，一个页面的慢文件读写不会阻塞另一个页面，关闭一个 WebUI 也只
会关闭它自己的 shell 与流。路径由 root shell 解析，可以读写模块目录、`/data/adb` 等普通
WebView 无权访问的位置；文件 API 的 shell 也不会与 `exec()` / 其它 Manager root 任务共享。

```javascript
import { io } from 'kernelsu';

if (io.support) {
  const file = io.File('/data/adb/modules/my_module/config.json');
  if (file.exists()) {
    console.log(file.length(), file.getAbsolutePath());
  }
}
```

- `io.support` `<boolean>` 当前 WebView 是否提供 `ksu.io`（为 `false` 时下面的调用都不可用）。
- `io.version` `<number>` 接口版本，目前为 `1`。
- 所有失败路径都会降级为安全默认值（布尔方法返回 `false`，`length()` / `lastModified()` 返回
  `-1`，`list()` / `listFiles()` 返回 `[]`，`open()` 返回空字符串），不会抛 JavaScript 异常。

### io.File(path)

返回一个文件对象；`path` 在 root shell 中解析。

- `exists()` / `isFile()` / `isDirectory()` / `canRead()` / `canWrite()` / `canExecute()` — `<boolean>`
- `createNewFile()` / `delete()` / `deleteRecursive()` / `mkdir()` / `mkdirs()` / `renameTo(destPath)` — `<boolean>`
- `list()` → `<string[]>` 文件名；`listFiles()` → `<string[]>` 绝对路径
- `length()` / `lastModified()` / `getFreeSpace()` / `getTotalSpace()` / `getUsableSpace()` → `<number>`
- `setLastModified(time)` / `setReadOnly()` / `setReadable(readable, ownerOnly)` /
  `setWritable(writable, ownerOnly)` / `setExecutable(executable, ownerOnly)` — `<boolean>`
- `getAbsolutePath()` / `getCanonicalPath()` / `getPath()` / `getName()` / `getParent()` /
  `toString()` → `<string>`
- `isHidden()` / `isBlock()` / `isCharacter()` / `isSymlink()` — `<boolean>`
- `createNewSymlink(target)` / `createNewLink(existing)` / `clear()` — `<boolean>`
- `newInputStream()` → `<string>` 流 id；`newOutputStream(append?)` → `<string>` 流 id

### io.FileInputStream()

- `open(path)` → `<string>` 流 id，失败或超过上限时为空字符串
- `read(id)` / `read(id, maxBytes)` → `<string>` **Base64** 数据；读到末尾或失败时为空字符串。
  默认每次最多 8 KiB，`maxBytes` 上限 64 KiB
- `available(id)` → `<number>`
- `close(id)` → `<boolean>`

### io.FileOutputStream()

- `open(path)` / `open(path, append)` → `<string>` 流 id
- `write(id, base64)` → `<boolean>` 写入 Base64 解码后的字节
- `writeByte(id, byte)` → `<boolean>`
- `flush(id)` / `close(id)` → `<boolean>`；写入后需要 `flush`/`close` 才会落盘

### io.RandomAccessFile()

- `open(path, mode)` → `<string>` 文件 id（`mode` 与 Java `RandomAccessFile` 相同，如 `"r"`、`"rw"`）
- `read(id)` → `<number>` 单字节；`readBytes(id, len)` → `<string>` Base64
- `readBoolean` / `readByte` / `readInt` / `readLong` / `readShort` / `readFloat` /
  `readDouble` / `readUTF` / `readLine`
- `write(id, b)` / `writeBase64(id, data)` / `writeBoolean` / `writeByte` / `writeInt` /
  `writeLong` / `writeShort` / `writeFloat` / `writeDouble` / `writeUTF`
- `seek(id, pos)` / `getFilePointer(id)` / `length(id)` / `setLength(id, newLength)` / `close(id)`

### 资源限制

- `FileInputStream` / `FileOutputStream` / `File.newInputStream()` / `File.newOutputStream()`
  合计最多同时打开 **50** 个流；
- `RandomAccessFile` 最多同时打开 **30** 个文件。

上限按**每个 WebUI Activity 的 ksu.io 会话**分别计算（不是全进程共享）。超过上限时 `open()`
返回空字符串（或对 `RandomAccessFile` 返回空 id），记得及时 `close(id)`。

## pm（包信息 API）

`pm` 映射到 Manager 注册的 `ksu.pm()` 接口（`ksu.pm().version() === 1`）。包清单来自 Manager
自己的包数据库，在 WebUI 打开时后台刷新；首次调用最多等待约 500 ms，如果 root 包查询较慢会
先返回空数组，稍后重试即可拿到完整数据（不会再阻塞 WebView）。

```javascript
import { pm } from 'kernelsu';

if (pm.support) {
  const userPackages = pm.listPackages('user');
  const info = pm.getPackagesInfo(['com.android.settings']);
  console.log(info[0].appLabel, info[0].versionName);
}
```

> **首次调用可能返回空数组**：Manager 正在后台刷新包清单，直接重试即可（建议只重试有限次数）。
> 重试会命中已填充的缓存，不会再次阻塞。
>
> ```javascript
> const loadPackages = (attempt = 0) => {
>   const packages = pm.listPackages('user');
>   if (packages.length === 0 && attempt < 10) {
>     // 500ms 后重试；后台刷新完成后该调用会立即返回完整列表
>     setTimeout(() => loadPackages(attempt + 1), 500);
>     return;
>   }
>   console.log('packages:', packages);
> };
> loadPackages();
> ```

- `pm.support` `<boolean>` 当前 WebView 是否提供 `ksu.pm`。
- `pm.version` `<number>` 接口版本，目前为 `1`。
- `pm.listPackages(type)` → `<string[]>`
  - `type`：`"user"`（第三方应用）、`"system"`（系统应用），其他值表示全部；
  - 返回包名数组（去重、排序）。
- `pm.getPackagesInfo(packages)` → `<Array>`
  - `packages` `<string[]>` 包名数组，或 JSON 字符串；
  - 按输入顺序返回，同一包名对应多个 UID 时会展开为多条，并按 `uid` 排序；
  - 每个条目字段：`packageName`、`versionName`、`versionCode`、`appLabel`、`isSystem`、
    `uid`、`userId`（`uid`/`userId` 未知时为 `null`）；
  - 找不到的包返回 `{ packageName, error }`。

> 旧版导出的 `listPackages(type)` / `getPackagesInfo(packages)` 仍然可用但已标记 deprecated，
> 新代码请使用 `pm.listPackages()` / `pm.getPackagesInfo()`。

## 下载 API

Manager 会在模块 WebUI 中自动注入 `download.js`，并注册 `ksu_download` JavaScript 接口。
带 `download` 属性的链接会被它接管：

- 页面里点击带 `download` 属性的 `<a>` 时自动下载；
- `blob:` / `data:` 资源、以及本模块内部的 `https://mui.kernelsu.org/...` 资源会被
  `fetch` 成 Blob 后保存到系统下载目录；**大于 10 MiB** 的文件自动走分块传输，其余走
  Base64 一次性传输；
- 带 `download` 属性但指向外部 `http(s)` 的链接交给系统浏览器打开，不会在 WebView 内导航；
  不带 `download` 属性的普通链接仍按 WebView 的正常导航规则处理。

### 自动下载（推荐）

```html
<a href="result.json" download="result.json">下载结果</a>
```

```javascript
// 也可以直接生成 Blob 并触发下载
const blob = new Blob(['hello'], { type: 'text/plain' });
const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = 'hello.txt';
document.body.appendChild(a);
a.click();
a.remove();
```

### 低级接口 `ksu_download`

`download.js` 注入成功后，页面可以直接使用全局 `ksu_download`：

```javascript
if (typeof ksu_download !== 'undefined') {
  // 小文件：直接把 Base64 交给 Manager
  ksu_download.save(btoa('hello'), 'hello.txt');

  // 大文件：分块写入，最后触发内部下载地址完成保存
  const downloadId = ksu_download.startChunkedDownload('big.bin', 'application/octet-stream');
  ksu_download.writeDownloadChunk(downloadId, base64Chunk0);
  ksu_download.writeDownloadChunk(downloadId, base64Chunk1);
  ksu_download.completeChunkedDownload(downloadId);

  const a = document.createElement('a');
  a.href = downloadId; // https://blob-download.kernelsu.internal/<id>
  a.download = 'big.bin';
  a.click();
}
```

- `save(base64, fileName?)` — 解码 Base64 后保存；`fileName` 可省略（默认 `download.bin`）。
- `startChunkedDownload(fileName?, mimeType?)` → `<string>` 内部下载地址（同时作为后续调用的
  `downloadId`），失败时为空字符串。
- `writeDownloadChunk(downloadId, base64Chunk)` → `<boolean>`
- `completeChunkedDownload(downloadId)` → `<boolean>`
- `cancelChunkedDownload(downloadId)` — 取消并清理临时文件。
- `requestPermissions()` — 按需请求下载所需的运行时权限（Android 13+ 通知权限 / Android 8–9
  存储权限）。`download.js` 已在点击下载时自动调用，通常不需要手动调用。
- `openExternal(url)` — 仅接受 `http` / `https`，交给系统浏览器打开。

> 分块下载完成后必须触发一次指向 `downloadId` 的下载（如上面的 `<a download>` 点击），
> WebView 的下载监听器才会把临时文件保存到下载目录。`download.js` 已经自动处理了这一点。

### 保存位置与权限

- **Android 10 (API 29) 及以上**：通过 MediaStore 写入公共 `Download/` 目录，不需要存储权限。
- **Android 8.0–9 (API 26–28)**：点击下载时按需请求 `WRITE_EXTERNAL_STORAGE`，获得权限后写入
  公共 `Download/` 目录（与 29+ 一致）；用户拒绝或外置存储不可用时回退到应用专属外部下载目录
  （`Android/data/<package>/files/Download/`）。
- 下载进度、完成、失败都会发出通知（渠道 `Module Downloads`）。**Android 13 (API 33) 及以上**
  点击下载时会按需请求 `POST_NOTIFICATIONS`；拒绝通知权限不会影响文件保存。
- 权限被拒时 WebUI 会弹一次 Toast 说明影响：通知权限被拒只关闭进度通知；存储权限被拒（API
  26–28）会说明文件改存应用专属下载目录。用户可在系统设置里重新授权。
- 公共 Download 目录保存后需要 MediaStore 扫描才能提供“打开”入口；扫描超时/失败时文件仍算
  保存成功（会提示下载完成），只是完成通知不带“打开”按钮。
- 文件名会去掉路径分隔符；应用专属目录中重名文件会自动加 ` (1)`、` (1)` 后缀，公共下载目录
  由系统处理重名。
- WebUI 关闭时未完成的缓存分块会被清理。

## Markdown 渲染（模块 README / 变更日志）

模块 README 与更新日志由 Manager 用 Kotlin commonmark 渲染成 HTML，再放进内嵌 WebView 展示；
渲染结果与样式不依赖模块自己提供的脚本。为兼容 GitHub 风格的图片/高度处理，WebView 额外加载
Manager 内置的 `assets/markdown.js`（GitHub 后处理脚本），并提供无副作用的
`window.native.sendMessage` stub。

- markdown.js 的后处理入口 `window.github.load(...)` 在页面加载完成后执行一次；调用时传入当前
  已渲染的 HTML，因此 **不会重新解析/覆盖 commonmark 生成的 DOM**，现有渲染保持不变。
- 后处理只做 GitHub 兼容修正：把 `github.com/.../blob/...` 形式的图片地址换成 `raw` 地址、
  处理 `raw.githubusercontent.com` 的转义路径，并注册高度监听（发往 stub，无副作用）。
- 页面内联脚本受严格 CSP 限制（只允许 Manager 自己的脚本 hash 与内置 markdown.js）；后处理
  代码异常会被捕获，不会影响 Markdown 内容展示。
- WebUI 页面（模块自己的 HTML/JS）不使用该渲染路径，模块自行渲染 Markdown。


