# JCEF 无法启动时的修复说明

IntelliDo 的登录、社区传输、受信任应用内浏览和身份安全页面都依赖 JCEF。JCEF 失败时不会降级到 JVM HTTP 客户端或系统浏览器会话。

## 请先检查

1. 使用 IntelliDo 捆绑的 JetBrains Runtime 启动，不要替换为不含 JCEF 的普通 JDK。
2. 在 Windows 上确认显卡驱动可用，并允许应用使用 GPU。
3. 暂时关闭会注入到 Chromium 进程的安全或杀毒软件后重试。
4. 查看诊断摘要中的 Java 供应商与版本：应来自捆绑 Runtime，而不是系统 JDK。
5. 重试初始化。若仍然失败，复制诊断信息并到 GitHub Issues 报告。

不要从系统浏览器导入 Cookie，也不要改用其他 HTTP 客户端绕过该边界。
