# 暂缓公开 IntelliDo 插件 API

IntelliDo 保留标准 IntelliJ Platform 插件系统和 Marketplace 安装能力，包括兼容的主题插件，但初始版本不承诺稳定的 IntelliDo 专属服务集成 API。未来辅助服务插件的身份验证、数据和 UI 要求明确后，再据此设计显式且带版本的扩展点。

插件管理器只展示并安装声明的依赖和构建范围与 IntelliDo 兼容的插件。本地插件归档也使用相同验证，且不提供强制安装不兼容插件的途径。不能仅因为 IntelliDo 使用 IntelliJ Platform，就展示普通 IDEA 开发插件。

与其他基于 IntelliJ 的产品一样，IntelliDo 把已安装插件视为受信任的进程内代码，而非沙箱扩展。首次安装 Marketplace 插件以及每次安装本地归档时，都显示明确的完全访问警告，包括其可能访问 LINUX DO 会话、本地文件、网络和操作系统。Marketplace 来源和签名信息保持可见，本地归档使用更强的警告措辞。

IntelliDo 可以自动检查 Marketplace 元数据以发现兼容插件更新，但绝不静默安装。成员明确批准单项更新或 **全部更新**，且只在更新后的插件需要时才重启。

IntelliDo 的无遥测保证不涵盖已安装的第三方插件。安装信任提示在可用时链接至插件自身的隐私信息，并说明外部代码可能独立发起网络请求。
