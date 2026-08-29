# IntelliDo 代理须知

每次会话先读本文件。`CLAUDE.md` 是指向本文件的符号链接。

权威来源（冲突时以它们为准，不要把全文贴进代码）：

- 术语：[CONTEXT.md](CONTEXT.md)
- 产品决策：[docs/adr/](docs/adr/)
- 贡献：[CONTRIBUTING.md](CONTRIBUTING.md)
- 工具链：[docs/toolchain.md](docs/toolchain.md)

本文件只收录**改错就会破坏产品边界**的约束。完整 ADR 仍以 `docs/adr/` 为准。

## 产品

IntelliDo 是面向 **LINUX DO** 的**非官方**独立桌面客户端：基于 IntelliJ Platform 的独立产品，不是安装到 IDEA 的插件。

当前 `0.x` 纵向切片：匿名 guest 经 JCEF 读取 `https://linux.do` 公开 JSON 并原生渲染；登录走 LINUX DO 真实页面，会话留在 JCEF。写入、Connect 权威进度、私信、Chat 尚未接通。`1.0.0` 留给已约定的完整范围。

禁止称作「IntelliDo 插件」「LINUX DO 插件」。获取、About、登录等身份边界必须出现「非官方 LINUX DO 客户端 / Unofficial LINUX DO Client」。

## 硬约束

括号内为 ADR 编号，改行为前先读原文。

### 身份与传输

- 只服务 LINUX DO（`https://linux.do`），不做可配置的其它 Discourse 社区（0002）。
- 永久只建模一个 LINUX DO 账户。禁止多账户、账户槽、并行会话；换人登录必须先原子清掉旧账户状态（0005）。
- 登录只发生在 LINUX DO 真实页面。禁止原生密码框，禁止从系统浏览器导入 Cookie（0003）。
- 已验证与匿名社区请求都走 JCEF，经窄桥把结构化结果交给原生 UI。禁止把会话 Cookie 交给独立 JVM HTTP 客户端，禁止再做一套并行 Discourse 传输（0004）。
- JCEF 起不来就封闭恢复：诊断、重试、修复说明、退出。禁止降级匿名浏览，禁止备用 HTTP 客户端（0071）。
- 身份安全（邮箱、密码、OAuth、Passkey、MFA、会话、删号）留在 LINUX DO 页面（0023）。
- 同一发布通道 + 同一操作系统用户只运行一个进程；稳定版与 Nightly 互斥（0061）。

### 内容与能力

- 可用操作以服务器对该资源返回的能力标志为准，再套永久 Level 3 上限。禁止按信任等级推断权限。禁止原生版主 / 管理员 / Level 4 工具（0022, 0009）。
- 原生功能只用结构化服务器响应。**唯一**允许解析渲染页 DOM 的是隔离的 Connect 适配器（0025）。Connect 权威状态与低等级估算必须区分，禁止把估算说成官方进度，禁止展示 Level 4（0024）。
- 帖子正文：cooked HTML → 领域模型 → IntelliJ/Swing 组件。禁止用 JCEF 渲染话题或帖子正文（0012）。
- 创作是原生 Markdown 优先编辑器，不做 WYSIWYG，不把未发布文本送给云端写作助手（0013）。
- LINUX DO 插件功能按已理解的契约原生支持；未知行为在最小范围内原生错误占位，禁止用嵌入式网站当核心体验（0011）。
- 暂缓公开 IntelliDo 专属插件 API（0026）。
- 交互用 IDEA 模式（Action、标签、工具窗口、随处搜索），语言用 Discourse 概念。禁止把话题叫成文件、论坛叫成项目（0008）。内部可以用 VirtualFile / FileEditor 实现标签，但用户可见文案不能泄漏这层隐喻。

### 持久化与在线

- 仅在线：无离线模式、无持久话题/帖子缓存、无写入队列、无事后补报阅读（0015）。
- 草稿只存在 LINUX DO 服务器。Chat / Boost 若无服务器草稿契约，输入只在内存里（0014）。
- 阅读活动以服务器为唯一账本；只上报真实注意力（焦点、可见、活跃）。匿名、后台、空闲、睡眠/锁屏都不计，恢复后不补计（0006, 0007, 0067）。
- 登录后只恢复导航目标，绝不自动重放 Like / 回复 / 投票等写入（0065）。
- 幂等读取可按限流退避重试；超时或结果不明的写入禁止盲目重试，先与服务器对账（0058）。
- 应用偏好只保存在本机，不接 JetBrains Settings Sync，不用云同步（0038）。
- 不收集产品遥测、使用情况或阅读分析（0035）。
- 每次启动从 Home 开始，标签页不跨启动恢复。异常退出只从服务器草稿恢复（0020, 0063）。
- 主动退出 / 退出登录前必须检查未同步内容；不能静默丢掉，也不能为 Chat/Boost 建本地草稿（0069, 0070）。

### 安全与浏览

- TLS 证书失败不可绕过，不提供「仍然继续」（0041）。
- 可见的应用内浏览使用随版本发布的精确 HTTPS Origin 允许列表。通配符、页面链接、远程配置不能授信；出列表的导航走系统浏览器（0030）。
- 持久保存**一个**受信任 JCEF 配置。从未登录的匿名会话只活在当前进程（0031）。
- 附件必须经原生保存对话框明确下载；禁止自动打开或执行。所有 JCEF 下载都走这条路径（0032）。
- IntelliDo 控制的 API 请求标明应用名、版本和项目 URL，不含安装 ID、账户 ID 或遥测 Token。面向用户的登录页保留普通 Chromium UA（0043）。
- JCEF、原生请求、Marketplace、更新检查共用同一代理策略，禁止某子系统静默直连（0042）。
- 操作系统通知默认不暴露标题、作者或正文。搜索私信 / Chat 必须显式选择范围，不能进默认「全部」（0017, 0018）。
- 最近搜索以服务器五项记录为准，不建本地搜索历史（0019）。

### 产品形态

- 独立 IntelliJ 产品；只交付最小能力面：Shell、原生编辑器、JCEF 边界、无障碍、本地化、兼容插件与更新。排除项目/工作区、VCS、构建、调试、终端、数据库、语言工具（0001, 0056）。
- 只用 IntelliJ New UI，不维护 Classic UI（0060）。
- 主题走 IntelliJ 生态，不复刻 LINUX DO 网站 CSS（0010）。
- 所有命令经 Action System 注册，可发现、可重映射、可无障碍（0029）。
- Kotlin 优先，Gradle Kotlin DSL；源码/目标 JDK 跟随稳定平台基线（当前 JDK 25、IntelliJ Platform **2026.2.1**）。禁止用 EAP 当产品基线，禁止 `latest` 或版本范围（0053, 0040）。
- 包名、插件 id：`moe.momokko.intellido`（0049）。
- 源码 Apache License 2.0；IntelliDo / LINUX DO 品牌资产不在该许可内（0034）。捆绑依赖必须可再分发；Copyleft 依赖需明确法律审查。
- 产品版本独立于 IntelliJ 构建号。`1.0.0` 表示约定范围完成，不提前宣称功能完整（0047, 0057）。
- 稳定产物只由公开 GitHub Actions 按受保护版本 Tag 构建；本机 `packageWindows` 只供安装验证（0052, 0037）。

## 语言

- 标识符、Package、API、KDoc、技术注释：英文（0062）。
- 产品 UI、仓库文档、用户材料：简体中文为先，英文回退（0027, 0050）。
- 新增用户可见字符串必须同时写：
  - `platform/src/main/resources/messages/IntelliDoBundle_zh_CN.properties`
  - `platform/src/main/resources/messages/IntelliDoBundle.properties`
- 通过 `IntelliDoStrings` 读取。缺失中文回退英文，禁止运行时机翻。社区内容与服务器标签保持原样。
- 自定义原生组件发布前必须有无障碍名称、状态、焦点顺序和完整键盘操作（0028）。媒体不自动播放。

## 模块

单进程模块化单体。禁止本地 Web 服务、配套后端、通用 IPC 或微服务（0054）。后台托盘是同一进程的精简状态，不是第二个守护进程（0016）。

| 模块 | 职责 | 不要放什么 |
| --- | --- | --- |
| `domain` | LINUX DO / Discourse 领域模型、cooked 解析 | IntelliJ API、网络、Swing |
| `transport` | `LinuxDoCommunityClient`、JSON 映射、Fake、JCEF 桥接客户端 | 带 Cookie 的 JVM HTTP、页面 DOM 抓取 |
| `platform` | 身份、i18n、单实例、Home 控制器、本地偏好 | Swing / IntelliJ UI |
| `browser` | JCEF 隔离、启动门闩、诊断 | 社区 JSON 业务解析 |
| `connect` | 唯一获准的 Connect 页面适配器 | 其它功能的 DOM 解析 |
| `ui` | 原生 UI、FileEditor 标签、JCEF 宿主 | 领域规则副本、第二条传输实现 |
| 根工程 | 产品组装、`runIde`、打包 | 业务逻辑 |

依赖方向：`domain ← transport ← platform ← {browser, connect} ← ui ← 根`。

生产路径：`BridgedLinuxDoCommunityClient` + JCEF JSON。测试与 `-Dintellido.transport=fake`：`FakeLinuxDoCommunityClient`。默认 `intellido.transport=jcef`。

## 命令

需要 JDK 25 与 Python 3。构建按 `scripts/vendor-lock.json` 拉取 Font Awesome / Twemoji，不把这些图标提交进 Git。

```bash
./gradlew check
./gradlew runIde
./gradlew runIde -Dintellido.transport=fake
./gradlew runIde -Pintellido.channel=nightly
./gradlew packageWindows
```

`check` 是 CI 入口。Kotlin `allWarningsAsErrors=true`。版本钉在 Version Catalog 与 lockfile。工具链选型见 `docs/toolchain.md`，不要擅自升到 EAP 或「更新的 Gradle」。

## 测试

**自动测试和 CI 绝不连接 linux.do**，不创建社区内容、不上报阅读活动（0055）。只用本地 Fake 与仓库内脱敏 Fixture：`transport/src/test/resources/discourse/`。

真实站点验证必须由人工控制。对照 LINUX DO chrome 时可用 headed Playwright，但页面快照、Cookie、Token、控制台日志不得进 Git。

## 本机 UI 自检

视觉改动后自己启动 `./gradlew runIde`（后台），把窗口拉到前台，截图到 `tmp/ui-smoke.png` 并阅读，修好再交。不要让用户重启来验证。`runIde` 经常在其它窗口后面打开。

停留在匿名公开内容，除非任务就是登录流。打开实际改动的界面（Home 列表、话题标签、侧栏），不要只看欢迎/加载态。

中文 Windows 上 2026.2 仍可能先弹出 JetBrains 语言/地区框。不要为此改用 `HeadlessToolkit` 去取菜单快捷键 mask。

## 实现陷阱

- 注册后禁止修改 `AnAction.templatePresentation`（2026.2 会抛）。
- 禁止在 `ToolWindowManagerListener.stateChanged` 里调用 `setAvailable` / `hide`（同步重入冻 EDT）。
- Home 是不可关闭的第一个标签；每个话题最多一个标签；不要做编辑器拆分（0020）。
- 不要把 IntelliDo 注册成系统浏览器或 `https://linux.do/` 链接处理器，不要发明 `intellido://`。
- 侧栏分类按**名称**匹配，不要依赖会过期的 seed id。
- 第三方媒体默认「仅加载一次」占位；折叠 / NSFW 默认隐藏，揭示状态不持久化（0012）。
- 帖子内 Web 嵌入是隔离内容岛：临时存储、无 LINUX DO 会话、无原生桥接、无自动播放。
- cooked 解析先走严格元素/属性/URL 允许列表，再创建组件。内容权威 ≠ 信任可执行标记。

## 仓库卫生与提交

不要提交：`tmp/`、`build/`、`.gradle/`、`.intellijPlatform/`、`.kotlin/`、`domain/src/main/resources/vendor/`、Playwright 快照/Cookie、根目录 `linux-do*.yml` / 截图、凭据。

每个 Commit 必须含 DCO 1.1：`git commit -s`。项目不要求 CLA。

严重安全漏洞发 [momokko@linux.do](mailto:momokko@linux.do) 或 GitHub 私密漏洞渠道，不要开公开 Issue。

## 当前切片

写入、后台托盘、Connect 权威进度、私信、Chat 等尚未接通。不要在任务范围之外「顺便」实现它们，也不要为它们加会破坏现有边界的捷径（第二条 HTTP 客户端、本地草稿、离线队列、多账户）。
