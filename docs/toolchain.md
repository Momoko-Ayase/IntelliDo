# 工具链选择依据

首个可构建切片固定使用下列**稳定**版本，不使用 EAP、动态版本或 `latest` 选择器。

| 组件 | 版本 | 依据 |
| --- | --- | --- |
| IntelliJ Platform / IntelliJ IDEA | **2026.2.1** | ADR 0040：从当时能通过 JCEF、主题、语言包和打包检查的最新稳定平台基线起步。JetBrains 于 2026-08-10 发布 2026.2.1（build `262.9437.185`），为 2026.2 线当时的最新稳定补丁。官方 SDK 文档示例使用 `intellijIdea("2026.2.0.1")`；产品实现采用已发布的 **2026.2.1** 补丁。自 2025.3 起 Community/Ultimate 辅助方法仅适用于更早版本，因此依赖声明使用统一的 `intellijIdea()`。 |
| IntelliJ Platform Gradle Plugin | **2.18.1** | 官方 IntelliJ Platform SDK《IntelliJ Platform Gradle Plugin (2.x)》在 2026-07-21 文档中推荐 2.18.1；GitHub Releases 显示 2.18.1 于 2026-07-10 发布，为当时最新稳定版。最低要求：Platform 2023.3、Gradle 9.0.0、Java 17。 |
| Gradle Wrapper | **9.5.1** | 满足插件的 Gradle ≥ 9.0.0。Kotlin Gradle Plugin 2.4.0–2.4.10 官方兼容范围为 Gradle 7.6.3–9.5.0；9.5.1（2026-05-12）是该 9.5 线上的补丁。未选用更新的 9.7.1，以避免超出 KGP 已公布范围。发行包 SHA-256：`bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f`。 |
| Kotlin | **2.4.10** | IntelliJ Platform 2026.2 捆绑 stdlib **2.4.0**。编译器使用 2.4 线的稳定补丁 2.4.10，避免跨主版本 stdlib 冲突。`kotlin.stdlib.default.dependency` 不在根产品中强制打包第二份 stdlib。 |
| 源码 / 目标 JDK | **25** | 跟随 2026.2 捆绑 JBR（日志为 25.0.3）。平台 API 以 JVM 25 字节码内联，产品源码目标必须匹配。本机另有 Temurin 25.0.2。 |
| JUnit | **5.13.4** | 纯 JVM 模块使用 JUnit 5。IntelliJ 集成测试使用 Platform 测试框架的 JUnit 5 类型，不连接 linux.do。 |
| JetBrains Runtime | 随 IDE 安装包 | `useInstaller` 默认为 true，JBR（含 JCEF）随官方 IDE 安装包提供，不另行挑选动态 JBR 版本。 |

依赖通过 Gradle Version Catalog 与 Wrapper 固定，并启用依赖锁定（`gradle.lockfile`）。发布构建禁止版本范围。

## 自定义产品形态

IntelliJ Platform Gradle Plugin 2.x 官方文档以**插件**为主，不提供完整 OEM 发行配方。IntelliDo 以该插件编译并启动独立进程，通过 `idea.platform.prefix`、`idea.paths.selector`、自定义 `ApplicationInfo.xml`、图标与启动画面把运行实例标识为 IntelliDo，而不是“安装到其他 IDE 的插件”。完整安装包重分发（Windows 每用户安装程序、ZIP、DMG、Tarball）仍受 ADR 0034/0037 约束，需在后续切片中只打包可再分发的平台模块。

开发期使用 `./gradlew runIde`：Gradle 插件会下载 IntelliJ IDEA 2026.2.1 安装包（含 JBR/JCEF），在 `.intellijPlatform/sandbox/` 中启动独立进程，并加载本仓库的 IntelliDo 插件。`runIde` 会按 `platform/src/main/resources/ide/kept-plugins.txt` 组装一份只含允许清单的 IDE 根目录（`build/stripped-ide`），从产品中**省略**编程/VCS/构建等捆绑插件，而不是把它们留在安装里再禁用。启动时设置 `intellij.platform.load.app.info.from.resources=true`，用 IntelliDo 的 `ApplicationInfo.xml` 覆盖 Ultimate 内嵌的 Java essential 插件声明。`idea.platform.prefix=IntelliDo` 已生效；`idea.paths.selector` 仍会被插件覆盖为 `IntelliJIdea2026.2`，真实安装目录隔离是后续发行切片的工作。

JCEF 匿名配置使用 2026.2.1 `JBCefAppCache` 读取的 `ide.browser.jcef.cache.path`（默认 `{system}/jcef_cache`）。IntelliDo 在 `JBCefApp.getInstance()` 之前把它设为 `{system}/jcef/{channel}/anonymous`，并关闭 `CefSettings.persist_session_cookies`。
