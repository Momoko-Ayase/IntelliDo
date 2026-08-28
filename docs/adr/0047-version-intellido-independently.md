# IntelliDo 独立版本化

IntelliDo 使用语义化 `MAJOR.MINOR.PATCH` 产品版本，不依赖其捆绑的 IntelliJ Platform 构建版本。Nightly 产物标识下一个预期产品版本、构建日期和源代码 Commit，例如 `1.2.0-nightly.20260822+abc1234`。准确的 IntelliJ Platform 和 JetBrains Runtime 构建仍分别显示在发布元数据、诊断和 About 中。

Nightly 构建使用独立产品身份和隔离的本地数据目录，但与稳定版共享 IntelliDo 跨通道单进程互斥。

其可见产品名为 **IntelliDo Nightly**，并配有带角标的应用与托盘图标。
