# WebUI（浏览器版管理器）

## 是什么

NekoSU WebUI 在设备本地启动一个 HTTP 服务（默认 `127.0.0.1:8787`），
用浏览器访问与管理器相同的临时 root 数据：root 状态、KMI、模块、超级用户、
日志中心与版本信息。

- 只绑定 `127.0.0.1`，不对外网开放
- 所有 `/api/*` 请求需要随机 token（`Authorization: Bearer <token>`）
- 前端静态资源内置在 `manager/app/src/main/assets/webui/`，无需联网
- 服务由前台 Service（`WebUiService`）保活，可在管理器设置中启动/停止
- 端口可选固定（8787）或随机（系统分配）；随机端口在每次启动后由服务回写实际端口

## 设置

Manager → 设置 → **WebUI（浏览器版）** 卡片：

| 开关 | 说明 |
|------|------|
| 启用 WebUI 服务 | 打开即启动前台服务与本地 HTTP 服务 |
| 随机端口 | 开启时由系统分配空闲端口；关闭时固定使用 8787 |
| 打开 WebUI 页面 | 进入详情页，查看地址/token、复制、在浏览器打开 |

## 打开方式

1. Manager → 设置 → **WebUI（浏览器版）** → 启用服务
2. 点击「启动」，复制地址与 token
3. 用设备浏览器打开 `http://127.0.0.1:8787/`（页面会内嵌 token）
   或手动访问 `http://127.0.0.1:8787/?token=<token>`

## 真正的 Web 前端

WebUI 是一套独立的前端服务（HTML/CSS/JS，随 Manager 打包，无需外网），
按 Manager 的 Material 3 布局重新实现，不是屏幕镜像：

- 首页：root 状态、KMI、内核、late-load、hook、运行信息
- 模块：模块列表与启用/禁用/移除/恢复
- 超级用户：授权应用与策略概要
- 日志中心：来源切换、搜索、导出
- 设置：WebUI 开关、随机端口、地址/token 复制、关于

样式与交互循 Material 3（顶栏、导航栏/底部导航、卡片、开关、chips），
手机浏览器为底部导航、桌面为侧边导航栏。

## 页面

| 页面 | 功能 |
|------|------|
| 仪表盘 | root 状态、KMI、内核版本、Late-load、hook 类型、管理器版本 |
| 模块 | 已安装模块列表，启用/禁用/移除/恢复 |
| 超级用户 | root 授权应用（uid、包名、策略概要） |
| 日志 | 日志中心（ALL / SU / Magica / Crash / Metamodule / Problem） |
| 服务 | WebUI 地址、token、复制、停止 |
| 关于 | 版本与项目定位（临时 root / 越狱模式） |

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/status` | 管理器版本 + 内核状态 |
| GET | `/api/modules` | 已安装模块 |
| POST | `/api/modules/{id}/(enable\|disable\|remove\|restore)` | 模块操作 |
| GET | `/api/superuser` | 超级用户分组 |
| GET | `/api/logs?source=ALL` | 日志文本 |
| GET | `/api/about` | 版本 / token |
| POST | `/api/refresh` | 刷新模块与超级用户缓存 |
| POST | `/api/stop` | 停止 WebUI 服务 |

## 安全

- token 每次安装随机生成（32 位十六进制），保存在应用私有 SharedPreferences
- 服务仅监听回环地址；同一设备上的其他 App 若获得 token 才可调用 API
- 浏览器关闭不会停止服务；在管理器或 WebUI「服务」页手动停止

## 限制

- 需要 Manager 进程/前台 Service 存活；进程被系统杀死后服务停止
- 覆盖常用页面，不是 100% 的 Compose UI 功能（如内核安装、主题编辑、App Profile 编辑暂未开放）
- 不提供远程访问；如需局域网访问需自行改绑定地址并注意风险
