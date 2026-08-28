# IntelliDo

IntelliDo 是面向 [LINUX DO](https://linux.do/) 的独立桌面客户端，提供 IDEA 风格的原生产品体验。

**非官方 LINUX DO 客户端 / Unofficial LINUX DO Client**

IntelliDo 并非由 LINUX DO 官方制作、认可或支持。

## 当前状态

本仓库已有可运行的 `0.x` 纵向切片。匿名 guest 通过 JCEF 读取 `https://linux.do` 公开 JSON 并原生渲染；测试与 CI 只用 Fake。登录与写入尚未接通。`1.0.0` 留给已约定的完整范围。

自动测试和 CI **不会**连接 linux.do、创建社区内容或上报阅读活动。

## 构建

需要 JDK 25（与 IntelliJ Platform 2026.2 捆绑的 JetBrains Runtime 一致）和 Python 3。构建会按 `scripts/vendor-lock.json` 拉取 Font Awesome 与 Twemoji，不把这些图标提交进 Git。

```bash
./gradlew check
./gradlew runIde
./gradlew packageWindows
```

`runIde` 会启动独立的 IntelliJ Platform 进程并加载 IntelliDo 产品插件（`moe.momokko.intellido`）。默认 `intellido.transport=jcef`，匿名浏览 LINUX DO 公开内容。单元测试与 CI 使用 Fake。本地强制 Fake：

```bash
./gradlew runIde -Dintellido.transport=fake
```

在中文 Windows 上，平台仍可能先弹出 JetBrains 语言/地区确认框；确认后才会进入 IntelliDo 的 JCEF 检测、启动画面和 Home 窗口。这是 2026.2 平台引导行为，后续切片会关掉或自动完成该对话框。

本机 Windows 每用户安装程序（及 ZIP）输出到 `build/dist/windows/`。这是维护者本机打包，用于安装验证，不是 GitHub Actions 按版本 Tag 发布的稳定版。

Nightly 身份：

```bash
./gradlew runIde -Pintellido.channel=nightly
```

版本选择依据见 [docs/toolchain.md](docs/toolchain.md)。产品决策见 [docs/adr/](docs/adr/)。术语见 [CONTEXT.md](CONTEXT.md)。贡献见 [CONTRIBUTING.md](CONTRIBUTING.md)。代理会话约束见 [AGENTS.md](AGENTS.md)。

## 许可

源代码以 [Apache License 2.0](LICENSE) 发布。IntelliDo 与 LINUX DO 的名称、图标及其他品牌资产不包含在该许可证中，详见 [NOTICE](NOTICE) 与 [artwork/README.md](artwork/README.md)。
