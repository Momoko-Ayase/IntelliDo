# IntelliDo

IntelliDo 是一款独立桌面客户端，为 LINUX DO 提供 IDEA 风格的原生产品体验。

## 术语

**IntelliDo**：
用于与 LINUX DO 交互的独立桌面产品。
_避免使用_：IntelliDo 插件、LINUX DO 插件

**LINUX DO**：
IntelliDo 服务的唯一在线社区。
_避免使用_：Discourse 实例、可配置社区、提供方

**登录（Sign-in）**：
在 LINUX DO 自身登录页完成的身份验证，IntelliDo 绝不接收成员凭据。
_避免使用_：原生登录、密码登录、IntelliDo 凭据

**LINUX DO 账户**：
成员在 IntelliDo 中使用的唯一社区身份；IntelliDo 没有独立账户系统，也绝不建模多个账户。
_避免使用_：IntelliDo 账户、账户槽位、Profile

**匿名模式（Anonymous mode）**：
退出登录后，只读浏览和搜索 LINUX DO 公开内容的体验；不提供个性化、交互、Connect、通知或服务器端升级能力。
_避免使用_：访客账户、匿名账户

**登录后返回（Post-sign-in return）**：
匿名成员在明确确认登录后返回此前需要账户的目标位置；只恢复导航上下文，绝不自动执行此前尝试的写入。
_避免使用_：登录后自动 Like、自动提交、重放匿名操作

**阅读活动（Reading activity）**：
LINUX DO 为已登录账户认可的话题、帖子和活跃阅读时间；LINUX DO 是该活动唯一的记录方和权威。
_避免使用_：本地阅读历史、离线阅读进度、待补报阅读进度

**活动中断（Activity interruption）**：
设备睡眠、休眠或操作系统用户会话锁定所形成的硬边界；IntelliDo 立即停止阅读计时，恢复后先重新校验服务器状态。
_避免使用_：暂停期间阅读、恢复后补计、连续后台阅读

**IDEA 风格体验（IDEA-like experience）**：
把 IntelliJ 风格交互模式应用于 LINUX DO，同时不以编程隐喻替代 Discourse 的社区概念或术语。
_避免使用_：论坛即项目、话题即文件、帖子即代码

**成员能力（Member capabilities）**：
LINUX DO 信任等级 0 至 3 的普通成员操作，构成 IntelliDo 永久的权限上限。
_避免使用_：工作人员工具、版主工具、管理员工具、Level 4 工具

**应用主题（Application theme）**：
成员选择的 IntelliJ Platform UI 主题，用于设置 IntelliDo 样式，与 LINUX DO 网站外观无关。
_避免使用_：LINUX DO 主题、Discourse 主题、网站 CSS

**用户脚本功能（User-script feature）**：
由成员安装的浏览器脚本添加至 LINUX DO 网站的行为；它不属于 IntelliDo 功能范围。
_避免使用_：核心功能、LINUX DO 插件

**插件功能（Plugin feature）**：
由服务器安装的 Discourse 插件提供的 LINUX DO 行为；理解其功能契约时，通过原生 IntelliDo 体验呈现。
_避免使用_：用户脚本功能、固定插件版本、Web 回退

**私信（Private message）**：
参与者受限的 Discourse 话题，使用 LINUX DO 提供的普通消息生命周期与权限。
_避免使用_：直接消息、Chat、本地消息

**Chat**：
LINUX DO 的实时频道与直接对话，拥有自身消息、Thread、回应、未读状态和保留语义。
_避免使用_：私信、话题、合并收件箱

**Web 嵌入（Web embed）**：
原生帖子中的隔离交互式 Web 内容岛，无法访问 LINUX DO 会话、IntelliDo 桥接或本地资源。
_避免使用_：Web 渲染帖子、已验证身份嵌入、任意应用内浏览器

**JCEF 恢复模式（JCEF recovery mode）**：
JCEF 无法初始化时显示的最小原生故障界面，只提供诊断、重试、修复说明和退出，不提供社区浏览或替代传输。
_避免使用_：降级匿名模式、备用 HTTP 客户端、无浏览器核心体验

**编辑器（Composer）**：
用于创建和修订 LINUX DO 内容的原生 Markdown 优先编辑器，具有结构化创作操作和原生预览。
_避免使用_：WYSIWYG 编辑器、Web 编辑器

**锁定编辑缓冲区（Locked composer buffer）**：
会话过期后仅在当前进程内保留、等待重新验证身份的未同步编辑文本；取消登录时只能复制或明确丢弃，绝不持久保存。
_避免使用_：本地恢复草稿、会话外编辑备份、自动提交

**草稿（Draft）**：
由 LINUX DO 持久保存的成员未发布内容；两次服务器自动保存之间，IntelliDo 只短暂持有内容。
_避免使用_：本地草稿、离线草稿、IntelliDo 草稿

**异常退出恢复（Abnormal-exit recovery）**：
异常终止后的下一次交互式启动仍进入 Home，并只提供打开 LINUX DO 服务器草稿的非模态入口；不恢复标签页、本地编辑状态或会话快照。
_避免使用_：标签页恢复、本地崩溃草稿、会话回放

**受限关机保存（Bounded shutdown save）**：
操作系统注销或关机时，对未同步编辑内容进行一次有严格时间上限的服务器草稿保存尝试；不能阻塞系统、建立本地副本或日后重放。
_避免使用_：无限等待关机、关机恢复文件、下次启动补交

**退出检查（Exit review）**：
成员主动退出 IntelliDo 时，对所有未同步创作内容进行的汇总交互检查；服务器草稿可保存，仅内存输入只能复制或明确丢弃，且成员可以取消退出。
_避免使用_：静默退出、强制关机流程、自动保存 Chat 输入

**退出登录检查（Sign-out review）**：
执行账户清理前对未同步创作内容进行的最后一次交互处置；成员可以保存、复制、明确丢弃或取消退出登录，但确认后不能保留任何账户时期内容。
_避免使用_：清理后恢复、后台保存、保留账户缓冲区

**仅在线（Online-only）**：
持久内容、创作、搜索、Connect 和活动功能都需要实时 LINUX DO 连接的运行模式；断线后，已显示内容可以短暂保持可见。
_避免使用_：离线模式、离线队列、持久内容缓存

**后台模式（Background mode）**：
正在运行的 IntelliDo 进程的可选精简托盘状态，仅为了接收并显示通知而保留已验证身份会话。
_避免使用_：独立守护进程、后台账户服务、会话导出 Helper

**应用实例（Application instance）**：
同一发布通道下、同一操作系统用户唯一运行的 IntelliDo 进程；后续启动请求会唤起该进程，并把受支持的启动目标转交给它。
_避免使用_：第二窗口、并行账户进程、重复后台进程

**私密通知（Private notification）**：
只显示 IntelliDo 和可选数量的操作系统通知；除非成员明确启用预览，否则绝不暴露社区元数据或内容。
_避免使用_：话题预览、发送者预览、标签预览、消息预览

**随处搜索（Search Everywhere）**：
通过双击 Shift 查找 IntelliDo Action、设置，以及有权访问的 LINUX DO 实体和普通内容的入口；私信与 Chat 必须明确选择搜索范围。
_避免使用_：默认搜索私密内容、浏览器搜索页

**最近搜索（Recent search）**：
LINUX DO 为已登录账户保留、由 IntelliDo 读取或更新的五项普通话题或帖子查询之一。
_避免使用_：本地搜索历史、Action 历史、设置历史、私密对话历史

**Home**：
由 LINUX DO 当前服务器配置的落地筛选器所选中的原生话题列表。
_避免使用_：IntelliDo 仪表盘、硬编码“最新”、恢复的会话

**未读帖子（Unread post）**：
位于 LINUX DO 所报告的已登录账户阅读位置之后的帖子。
_避免使用_：本地未读状态、恢复的滚动位置

**可用操作（Available action）**：
LINUX DO 为当前资源明确授予，且未被 IntelliDo Level 3 上限排除的成员能力。
_避免使用_：根据信任等级推断的操作、本地授予的操作

**Boost**：
附加到特定帖子的简短自由文本微回应，不创建新帖子。
_避免使用_：回复、帖子、Like、Chat 消息

**社区偏好（Community preference）**：
通过 IntelliDo 原生 UI 管理的 LINUX DO Profile、通知、界面、跟踪、分类、标签、Chat 或插件选项。
_避免使用_：身份安全

**应用偏好（Application preference）**：
控制 IntelliDo 自身外观或行为的设备本地选择；绝不通过 LINUX DO、JetBrains 或 IntelliDo 服务同步。
_避免使用_：社区偏好、云端偏好、同步设置

**设置归档（Settings archive）**：
由成员明确导出或导入的本地迁移文件，只包含允许列表中的非敏感设备设置，并在写入前展示准确内容清单。
_避免使用_：账户备份、会话备份、插件备份、云同步

**源码语言（Source language）**：
源码标识符、公共 API、KDoc 和技术性代码注释统一使用英文；产品 UI、仓库文档和面向用户的材料仍以简体中文为先。
_避免使用_：中文标识符、中英混合 API、仅英文产品文档

**Marketplace 身份（Marketplace identity）**：
仅用于购买或激活 Marketplace 付费插件许可证的可选 JetBrains 身份；与成员的 LINUX DO 身份无关，且绝不同步 IntelliDo 偏好。
_避免使用_：IntelliDo 账户、设置账户、LINUX DO 账户

**安全模式（Safe mode）**：
禁用所有非捆绑插件的 IntelliDo 恢复启动，使损坏的插件或主题无法阻止访问应用及其恢复控件。
_避免使用_：匿名模式、重置、重新安装

**本地应用数据（Local application data）**：
设备管理的 IntelliDo 状态，例如受信任会话、浏览器存储、应用偏好、日志、缓存和非捆绑插件；不包括 LINUX DO 服务器数据以及成员明确下载到其他位置的文件。
_避免使用_：LINUX DO 数据、已下载附件、阅读历史

**身份安全（Identity security）**：
由 LINUX DO 拥有的凭据和账户控制操作，包括已验证邮箱、密码、OAuth 身份、Passkey、MFA、恢复、会话、已授权应用和删除账户。
_避免使用_：原生安全设置、IntelliDo 凭据

**升级状态（Upgrade status）**：
由 LINUX DO Connect 提供的已登录账户权威信任等级要求进度。
_避免使用_：本地计算进度、预测升级

**升级估算（Upgrade estimate）**：
当 Connect 状态不可用时，对 Level 0–1 LINUX DO Summary 统计与已公布升级要求进行的明确非权威比较。
_避免使用_：Connect 状态、保证进度、升级积分

**辅助服务（Auxiliary service）**：
位于主 Discourse 站点和 Connect 之外、与 LINUX DO 相关的系统，例如 Credit 或 CDK；它超出当前核心范围，日后可由 IntelliDo 插件集成。
_避免使用_：核心功能、插件功能、捆绑服务

**受信任服务（Trusted service）**：
已安装 IntelliDo 版本明确允许其精确 HTTPS Origin 在可见应用内浏览器中打开的辅助 Web 服务。
_避免使用_：链接网站、通配符子域名、远程信任 Origin

**退出登录（Sign-out）**：
清除专用受信任浏览器配置、结束后台模式、关闭账户范围内容并使 IntelliDo 返回匿名 Home 的操作。
_避免使用_：切换账户、仅 UI 退出、保留受信任会话

**非官方客户端（Unofficial client）**：
IntelliDo 与 LINUX DO 的关系：可以使用社区品牌，但并非由 LINUX DO 官方制作、认可或支持的产品。
_避免使用_：官方客户端、附属客户端
