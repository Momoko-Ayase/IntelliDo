# 贡献指南

欢迎通过 GitHub Issues、Pull Request 参与 IntelliDo。英文来稿同样欢迎。

严重安全漏洞请直接发送到 [momokko@linux.do](mailto:momokko@linux.do)，不要开公开 Issue。普通缺陷请使用 [缺陷报告](https://github.com/Momoko-Ayase/IntelliDo/issues/new?template=bug.yml) 模板。

## Developer Certificate of Origin

每一份提交到本仓库的 Commit 都必须包含 [DCO 1.1](https://developercertificate.org/) 的 `Signed-off-by` 行。项目不要求单独的 Contributor License Agreement。

签署表示你有权按 Apache License 2.0 提交该作品，并且该签署会成为公开 Commit 记录的一部分。

```text
Signed-off-by: Your Name <you@example.com>
```

可使用 `git commit -s` 添加。

## 源码与产品语言

- 源码标识符、API、KDoc 和技术注释使用英文。
- 产品 UI、仓库文档和面向用户的材料以简体中文为先，并提供英文回退。

## 测试

不要编写会连接 LINUX DO、创建社区内容或上报阅读活动的自动测试。使用本地 Fake 与脱敏 Fixture。

## 仓库卫生

不要提交本机缓存、抓取或会话材料，包括：

- `tmp/`、`build/`、`.gradle/`、`.intellijPlatform/`、`.kotlin/`
- 构建拉取的 Font Awesome / Twemoji（`domain/src/main/resources/vendor/`、`domain/build/generated/`）
- Playwright / 浏览器页面快照、Cookie、Token、控制台日志
- 根目录误放的 `linux-do*.yml`、`linuxdo-*.yaml` 或烟雾测试截图

自动测试只使用仓库内的 Fake Fixture（例如 `transport/src/test/resources/discourse/`）。真实站点验证必须由人工控制，且不得把页面快照或凭据送进 Git。
