package moe.momokko.intellido.transport

import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicPoster
import java.time.Instant

/**
 * Local visual contract for Discourse cooked HTML.
 *
 * Discourse compiles Markdown and BBCode on the server. The client deliberately
 * does not compile either language again; every sample therefore shows the
 * authoring source in a code block followed by representative cooked HTML that
 * travels through the same parser and native renderer as a real post.
 */
object DiscourseFormatPreview {
    const val TOPIC_ID: Long = 106L

    val topic: HomeTopic = HomeTopic(
        id = TOPIC_ID,
        title = "Discourse 格式预览：Markdown、BBCode 与扩展",
        slug = "discourse-format-preview",
        postsCount = 4,
        replyCount = 3,
        categoryName = "使用指南",
        authorUsername = "helper",
        lastPostedAt = Instant.parse("2026-08-22T06:03:00Z"),
        categoryIcon = "book",
        tags = listOf("faq", "intellido"),
        views = 1,
        pinned = true,
        posters = listOf(TopicPoster("helper")),
        wordCount = 900,
    )

    val posts: List<TopicPost> by lazy { listOf(
        TopicPost(
            id = 6001,
            postNumber = 1,
            username = "helper",
            displayName = "格式测试",
            cookedHtml = markdownCooked,
            plainText = "Markdown 基础格式预览：标题、强调、链接、列表、引用、代码、表格和分隔线。",
            createdAt = Instant.parse("2026-08-22T06:00:00Z"),
        ),
        TopicPost(
            id = 6002,
            postNumber = 2,
            username = "helper",
            displayName = "格式测试",
            cookedHtml = bbCodeCooked,
            plainText = "BBCode 格式预览：文字样式、颜色、字号、对齐、引用、代码和隐藏内容。",
            createdAt = Instant.parse("2026-08-22T06:01:00Z"),
        ),
        TopicPost(
            id = 6003,
            postNumber = 3,
            username = "helper",
            displayName = "格式测试",
            cookedHtml = discourseCooked,
            plainText = "Discourse 扩展预览：提及、标签、日期、脚注、详情、提示、Onebox、投票、聊天和公式。",
            createdAt = Instant.parse("2026-08-22T06:02:00Z"),
        ),
        TopicPost(
            id = 6004,
            postNumber = 4,
            username = "helper",
            displayName = "格式测试",
            cookedHtml = mediaCooked,
            plainText = "媒体预览：图片、图库、SVG、音频、语音、视频和安全的外部嵌入占位。",
            createdAt = Instant.parse("2026-08-22T06:03:00Z"),
        ),
    ) }

    private val markdownCooked: String = """
        <h2 id="markdown-preview">Markdown 基础格式</h2>
        <p><em>上方是输入源码，下方是 Discourse cooked HTML 的原生预览。</em></p>
        <pre><code class="lang-markdown"># 一级标题
## 二级标题

**粗体**、*斜体*、~~删除线~~、`行内代码`
[安全链接](https://example.com/docs)

&gt; 引用内容

- 无序项目
- [x] 已完成任务

1. 第一项
2. 第二项

```kotlin
fun preview() = "native"
```

| 格式 | 状态 |
| --- | --- |
| 表格 | 支持 |</code></pre>
        <hr>
        <h1>一级标题</h1>
        <h2>二级标题</h2>
        <h3>三级标题</h3>
        <p><strong>粗体</strong>、<em>斜体</em>、<s>删除线</s>、<code>行内代码</code>、<kbd>Ctrl</kbd> + <kbd>K</kbd>，以及<a href="https://example.com/docs">安全链接</a>。<br>这一行用于检查显式换行。</p>
        <blockquote><p>引用内容支持<strong>嵌套样式</strong>。</p><blockquote><p>这是第二层引用。</p></blockquote></blockquote>
        <ul>
          <li>无序项目</li>
          <li><span class="chcklst-box checked"></span> 已完成任务</li>
          <li><span class="chcklst-box"></span> 未完成任务</li>
        </ul>
        <ol start="3"><li>从第三项开始</li><li><p>列表中的段落</p><ul><li>嵌套项目</li></ul></li></ol>
        <pre><code class="lang-kotlin">fun preview() = "native"
println(preview())</code></pre>
        <table>
          <thead><tr><th>格式</th><th>状态</th><th>窄屏表现</th></tr></thead>
          <tbody><tr><td>表格</td><td><strong>支持</strong></td><td>单元格自动折行</td></tr><tr><td>长内容</td><td>可读</td><td>不会越过话题时间线</td></tr></tbody>
        </table>
        <hr>
        <p>Markdown 基础预览结束。</p>
    """.trimIndent()

    private val bbCodeCooked: String = """
        <h2 id="bbcode-preview">BBCode 格式</h2>
        <pre><code class="lang-bbcode">[b]粗体[/b] [i]斜体[/i] [u]下划线[/u] [s]删除线[/s]
[color=#2e7d32]绿色[/color] [size=150]放大文字[/size]
[url=https://example.com]链接[/url]
[center]居中[/center] [right]右对齐[/right]
[quote="helper"]带署名引用[/quote]
[code]val answer = 42[/code]
[spoiler]行内答案[/spoiler]
[details="展开 BBCode"]折叠内容[/details]</code></pre>
        <p><strong>粗体</strong>、<em>斜体</em>、<u>下划线</u>、<s>删除线</s>、<mark>高亮</mark>，H<sub>2</sub>O 与 x<sup>2</sup>。</p>
        <p><span style="color:#2e7d32">绿色文字</span>、<span style="background-color:#fff2a8">背景色</span>、<span style="font-size:150%">放大文字</span>。</p>
        <p><a href="https://example.com">BBCode 安全链接</a>，以及 <span class="spoiled">行内隐藏答案</span>。</p>
        <div style="text-align:center"><p>居中文字</p></div>
        <div style="text-align:right"><p>右对齐文字</p></div>
        <aside class="quote" data-username="helper" data-post="1" data-topic="106"><blockquote><p>带署名的 BBCode 引用。</p></blockquote></aside>
        <pre><code class="lang-kotlin">val answer = 42</code></pre>
        <div class="spoiler"><p>块级隐藏内容，只在主动展开后显示。</p></div>
        <details><summary>展开 BBCode 详情</summary><p>折叠区域中的<strong>富文本</strong>与列表：</p><ul><li>详情项目</li></ul></details>
        <p>BBCode 格式预览结束。</p>
    """.trimIndent()

    private val discourseCooked: String = """
        <h2 id="discourse-preview">Discourse 与主题扩展</h2>
        <p>提及 <a class="mention" href="/u/helper">@helper</a>，标签 <a class="hashtag-cooked" href="/tag/intellido"><span>intellido</span></a>，附件 <a class="attachment" href="/uploads/preview.txt">preview.txt</a>，Emoji <img class="emoji" src="/images/emoji/twitter/slight_smile.png?v=12" title=":slight_smile:" alt="🙂" width="20" height="20">，行内图标 <img src="/uploads/inline-icon.png" alt="行内图标" width="24" height="24">。</p>
        <p>本地日期 <span class="discourse-local-date" data-date="2026-08-22" data-time="14:30" data-timezone="Asia/Shanghai">2026-08-22 14:30</span>，行内公式 <span class="math">E = mc^2</span>。</p>
        <p>如开头所说，顶点是新起点，接下来该怎么做就成了需要直面的首要问题。摆在社区面前有两条很不同的路（恰如去年<a href="/t/topic/188585" data-clicks="2.1k">向左还是向右的路线问题</a>）：野蛮生长和压实深耕。是选择趁这个势头急速扩张，进一步扩大影响，一味吸纳更多的成员，营造更热闹的声势？还是在热闹面前冷静下来，压实和沉淀，调整找到最佳状态？</p>
        <p>带脚注的正文<sup class="footnote-ref"><a href="#fn:preview">1</a></sup>。</p>
        <blockquote><p>[!tip]+ 原生提示<br>Callout 支持标题、正文、列表和折叠状态。</p><ul><li>提示项目</li></ul></blockquote>
        <div class="callout callout-warning" data-callout="warning"><div class="callout-title">警告提示</div><div class="callout-content"><p>这是 class 形式的提示区块。</p></div></div>
        <details open><summary>默认展开的 Details</summary><p>详情正文在当前帖子内原生排版。</p></details>
        <aside class="onebox" data-onebox-src="https://example.com/article">
          <header class="source"><a href="https://example.com/article">example.com</a></header>
          <article class="onebox-body"><h3><a href="https://example.com/article">Onebox 预览卡片</a></h3><p>链接摘要会以原生卡片显示。</p></article>
        </aside>
        <div class="poll" data-poll-name="preview" data-poll-question="原生预览是否清晰？" data-poll-type="multiple" data-poll-status="open"><ul>
          <li data-poll-option-id="yes"><span class="option-text">清晰且容易阅读</span></li>
          <li data-poll-option-id="narrow"><span class="option-text">窄窗口下仍然自动换行</span></li>
        </ul></div>
        <div class="math">x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}</div>
        <div class="policy" data-version="2" data-groups="everyone"><div class="policy-body"><p>Policy 区块</p><ul><li>友善交流</li></ul></div></div>
        <div class="chat-transcript" data-username="alice" data-channel-name="general" data-datetime="2026-08-22T14:30:00+08:00"><div class="chat-transcript-messages"><p>Chat Transcript 消息内容。</p></div></div>
        <dl><dt>Cooked HTML</dt><dd><p>Discourse 在服务器端生成、由客户端安全渲染的帖子内容。</p></dd></dl>
        <section class="footnotes"><ol class="footnotes-list"><li id="fn:preview"><p>脚注正文，支持<strong>格式</strong>。<a class="footnote-backref" href="#fnref:preview">↩</a></p></li></ol></section>
        <p>Discourse 扩展预览结束。</p>
    """.trimIndent()

    private val mediaCooked: String = """
        <h2 id="media-preview">媒体与安全降级</h2>
        <p>远程媒体在本地测试中不自动联网；图片显示原生占位，音视频与 iframe 显示可激活的原生卡片。</p>
        <svg viewBox="0 0 320 120" width="320" height="120" role="img"><title>本地 SVG 预览</title><rect width="320" height="120" rx="12" fill="#365880"></rect><circle cx="60" cy="60" r="32" fill="#f6c344"></circle><path d="M115 78 L150 42 L185 78 Z" fill="#ffffff"></path></svg>
        <p><em>安全清理并栅格化的内联 SVG</em></p>
        <figure><img src="/uploads/optimized/preview.png" alt="单张图片占位" width="640" height="360"><figcaption>Figure 图片说明</figcaption></figure>
        <div class="d-image-grid" data-columns="2">
          <div class="lightbox-wrapper"><a class="lightbox" href="/uploads/original/grid-a.png"><img src="/uploads/optimized/grid-a.png" alt="图库图片 A" width="600" height="400"></a></div>
          <div class="lightbox-wrapper"><a class="lightbox" href="/uploads/original/grid-b.png"><img src="/uploads/optimized/grid-b.png" alt="图库图片 B" width="600" height="400"></a></div>
        </div>
        <audio controls><source src="/uploads/audio-preview.mp3" type="audio/mpeg"></audio>
        <div class="d-wrap" data-wrap="voice"><audio controls><source src="/uploads/voice-preview.m4a" type="audio/mp4"></audio></div>
        <video controls width="640" height="360" poster="/uploads/video-poster.png"><source src="/uploads/video-preview.mp4" type="video/mp4"></video>
        <div class="video-placeholder-container" data-video-src="/uploads/lazy-video.mp4" data-thumbnail-src="/uploads/lazy-poster.png" data-orig-src="upload://lazy-video.mp4"></div>
        <div class="lazy-video-container" data-video-title="外部视频预览" data-provider-name="example"><a class="title-link" href="https://example.com/video">在外部打开</a></div>
        <iframe data-src="https://example.com/embed" width="640" height="360" title="安全嵌入占位"></iframe>
        <p>媒体与安全降级预览结束。</p>
    """.trimIndent()
}
