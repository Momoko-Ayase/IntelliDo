# 标识由 IntelliDo 控制的请求

由 IntelliDo 控制的 LINUX DO API 和通知客户端所生成的请求，会标识应用名称、版本和公开项目 URL。它们不包含安装 ID、账户 ID 或遥测 Token。面向用户的嵌入式登录和受信任服务浏览保留普通 Chromium User-Agent，避免全产品 User-Agent 覆盖改变身份验证、Passkey、OAuth 或浏览器兼容性。
