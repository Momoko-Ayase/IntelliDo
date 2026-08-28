# 使用 Kotlin 优先的 IntelliJ Platform 构建

IntelliDo 以 Kotlin 为先，并使用 Gradle Kotlin DSL 及当前受维护的 IntelliJ Platform Gradle Plugin。若上游 API 互操作性或显著更清晰的代码足以证明合理，仍可使用 Java；Kotlin 本身不是强制目标。源码和目标 JDK 跟随所选稳定 IntelliJ Platform 基线，而不是独立冻结某个 Java 版本。

Gradle Wrapper、IntelliJ Platform、构建插件及各库均完整固定版本并启用依赖锁定。发布构建不包含动态 `latest` 选择器或版本范围；自动化可以提出更新，但每项变更都需要人工审查。
