# 绝不绕过 TLS 信任失败

只有证书链受到操作系统或捆绑 Runtime 信任时，IntelliDo 才允许 HTTPS 连接。对于 LINUX DO、Connect、允许列表中的受信任服务、更新源与产物以及隔离 Web 嵌入，证书失败均不可绕过。IntelliDo 会显示可诊断错误，但绝不提供 **仍然继续**，因为这些界面可能承载身份验证或成员内容。
