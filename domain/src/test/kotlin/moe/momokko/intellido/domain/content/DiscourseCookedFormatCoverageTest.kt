package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscourseCookedFormatCoverageTest {
    private val parser = CookedHtmlParser()

    @Test
    fun `inline discourse formatting remains structured and inert`() {
        val document = parser.parse(
            """
            <h2 id="chapter" style="text-align:center">章节</h2>
            <p><strong>粗体</strong><em>斜体</em><s>删除</s><u>下划线</u><mark>高亮</mark>
              H<sub>2</sub>O x<sup>2</sup> <kbd>Ctrl</kbd> <code>run()</code>
              <span style="color:hsl(210, 50%, 40%);background-color:#ffeeaa;font-size:125%">彩色</span>
              <span class="spoiled">答案</span>
              <span class="discourse-local-date" data-date="2026-08-15" data-time="14:30" data-timezone="Asia/Shanghai">2026年8月15日 下午2:30</span>
              <span class="math">E = mc^2</span><span class="click-count">12 次点击</span>
            </p>
            """.trimIndent(),
        )

        val heading = document.blocks.filterIsInstance<CookedBlock.Heading>().single()
        assertEquals(CookedAlignment.Center, heading.alignment)
        val spans = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single().spans.flattened()
        assertTrue(spans.any { it is CookedSpan.Strong })
        assertTrue(spans.any { it is CookedSpan.Emphasis })
        assertTrue(spans.any { it is CookedSpan.Strike })
        assertTrue(spans.any { it is CookedSpan.Underline })
        assertTrue(spans.any { it is CookedSpan.Highlight })
        assertTrue(spans.any { it is CookedSpan.Subscript })
        assertTrue(spans.any { it is CookedSpan.Superscript })
        assertTrue(spans.any { it is CookedSpan.Kbd })
        assertTrue(spans.any { it is CookedSpan.Code })
        assertTrue(spans.any { it is CookedSpan.Colored && it.background == "#ffeeaa" })
        assertTrue(spans.any { it is CookedSpan.Sized && it.scale == 1.25f })
        assertTrue(spans.any { it is CookedSpan.Spoiler })
        assertTrue(spans.any { it is CookedSpan.LocalDate })
        assertTrue(spans.any { it is CookedSpan.Math })
        assertTrue(spans.any { it is CookedSpan.ClickCount })
        assertFalse(document.plainText.contains("答案"), document.plainText)
    }

    @Test
    fun `callouts folds policy chat and definitions preserve their native structure`() {
        val document = parser.parse(
            """
            <blockquote><p>[!tip]+ 进阶技巧<br>不要在 CI 上刷新基线。</p><ul><li>检查 diff</li></ul></blockquote>
            <details open><summary>展开说明</summary><p>默认可见</p></details>
            <div class="spoiler blurred"><p>剧透内容</p></div>
            <div class="policy" data-version="2" data-groups="staff"><p>社区规则</p><ul><li>友善交流</li></ul></div>
            <div class="chat-transcript" data-username="alice" data-channel-name="general" data-datetime="2026-02-12T10:30:00Z">
              <div class="chat-transcript-user"><span>元数据不会混入正文</span></div>
              <div class="chat-transcript-messages"><p>大家好</p></div>
            </div>
            <dl><dt>术语</dt><dd><p>第一段释义</p><ul><li>要点</li></ul></dd></dl>
            <future-widget><p>未来格式的可读内容</p></future-widget>
            """.trimIndent(),
        )

        val callout = document.blocks.filterIsInstance<CookedBlock.Callout>().single()
        assertEquals(CookedBlock.Callout.Kind.Tip, callout.kind)
        assertEquals("进阶技巧", callout.title)
        assertTrue(callout.collapsible)
        assertTrue(callout.initiallyOpen)
        assertTrue(callout.children.joinToString(" ") { it.plainText() }.contains("检查 diff"))
        val details = document.blocks.filterIsInstance<CookedBlock.Details>().single()
        assertTrue(details.initiallyOpen)
        assertEquals("默认可见", details.children.single().plainText())
        assertEquals("2", document.blocks.filterIsInstance<CookedBlock.Policy>().single().version)
        val chat = document.blocks.filterIsInstance<CookedBlock.ChatTranscript>().single()
        assertEquals("alice", chat.username)
        assertEquals("大家好", chat.children.single().plainText())
        val definitions = document.blocks.filterIsInstance<CookedBlock.DefinitionList>().single()
        assertEquals("术语", definitions.items.single().term.plainText())
        assertTrue(definitions.items.single().definitions.flatten().any { it is CookedBlock.ListBlock })
        val unknown = document.blocks.filterIsInstance<CookedBlock.Unknown>().single()
        assertEquals("未来格式的可读内容", unknown.children.single().plainText())
    }

    @Test
    fun `images audio video embeds svg and math become native media nodes`() {
        val document = parser.parse(
            """
            <div class="d-image-grid" data-columns="2">
              <div class="lightbox-wrapper"><a class="lightbox" href="https://linux.do/uploads/full1.jpg"><img src="/uploads/thumb1.jpg" alt="图一" width="600" height="400"></a></div>
              <div class="lightbox-wrapper"><a class="lightbox" href="https://linux.do/uploads/full2.jpg"><img src="/uploads/thumb2.jpg" alt="图二" width="600" height="400"></a></div>
            </div>
            <p><div class="video-placeholder-container" data-video-src="/uploads/movie.mp4" data-thumbnail-src="/uploads/poster.png" data-orig-src="upload://movie.mp4"></div></p>
            <div class="d-wrap" data-wrap="voice"><audio controls><source src="/uploads/voice.m4a" type="audio/mp4"></audio></div>
            <iframe data-src="https://player.bilibili.com/player.html?bvid=BVxxx" width="800" height="450"></iframe>
            <div class="lazy-video-container" data-video-title="演示视频" data-provider-name="youtube"><a class="title-link" href="https://www.youtube.com/watch?v=abc">打开</a></div>
            <div class="math">x = \frac{-b}{2a}</div>
            <svg viewBox="0 0 20 10" onload="alert(1)"><script>alert(2)</script><rect width="20" height="10" fill="#4c8bf5"/><image href="https://evil.example/pixel.png"/></svg>
            """.trimIndent(),
        )

        val grid = document.blocks.filterIsInstance<CookedBlock.ImageGrid>().single()
        assertEquals(2, grid.images.size)
        assertTrue(grid.images.all { it.trusted })
        assertEquals("https://linux.do/uploads/full1.jpg", grid.images.first().original)
        val media = document.blocks.filterIsInstance<CookedBlock.Media>()
        assertTrue(media.any { it.kind == CookedBlock.Media.Kind.Video && it.src == "https://linux.do/uploads/movie.mp4" })
        assertTrue(media.any { it.kind == CookedBlock.Media.Kind.Voice && it.src == "https://linux.do/uploads/voice.m4a" })
        assertTrue(media.any { it.kind == CookedBlock.Media.Kind.Embed && it.src.startsWith("https://player.bilibili.com/") })
        assertTrue(media.any { it.kind == CookedBlock.Media.Kind.Embed && it.title == "演示视频" })
        assertEquals("x = \\frac{-b}{2a}", document.blocks.filterIsInstance<CookedBlock.Math>().single().latex)
        val svg = document.blocks.filterIsInstance<CookedBlock.Svg>().single()
        assertFalse(svg.source.contains("script", true), svg.source)
        assertFalse(svg.source.contains("onload", true), svg.source)
        assertFalse(svg.source.contains("evil.example", true), svg.source)
    }

    @Test
    fun `footnotes polls and malformed html retain content without executable urls`() {
        val document = parser.parse(
            """
            <p title="1 > 0">正文 <sup class="footnote-ref"><a href="#fn:1">1</a></sup>
              <a href="javascript:alert(1)">危险链接文字</a></p>
            <hr class="footnotes-sep">
            <section class="footnotes"><ol class="footnotes-list"><li id="fn:1"><p>脚注 <strong>正文</strong><a class="footnote-backref" href="#fnref:1">↩︎</a></p></li></ol></section>
            <div class="poll" data-poll-name="favorite" data-poll-question="你最喜欢哪个?" data-poll-type="multiple" data-poll-status="closed">
              <ul><li data-poll-option-id="x"><span class="option-text">Flutter</span><span class="percentage">80%</span></li></ul>
            </div>
            """.trimIndent(),
        )

        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        val reference = paragraph.spans.flattened().filterIsInstance<CookedSpan.FootnoteRef>().single()
        assertEquals("脚注 正文", reference.content.plainText().replace(Regex("\\s+"), " ").trim())
        assertTrue(paragraph.spans.none { it is CookedSpan.Link && it.href.startsWith("javascript") })
        val footnotes = document.blocks.filterIsInstance<CookedBlock.Footnotes>().single()
        assertEquals("1", footnotes.entries.single().number)
        val poll = document.blocks.filterIsInstance<CookedBlock.Poll>().single()
        assertEquals("你最喜欢哪个?", poll.title)
        assertTrue(poll.multiple)
        assertEquals("closed", poll.status)
        assertEquals(listOf("Flutter"), poll.options)
    }

    private fun List<CookedSpan>.flattened(): List<CookedSpan> = flatMap { span ->
        listOf(span) + when (span) {
            is CookedSpan.Strong -> span.children.flattened()
            is CookedSpan.Emphasis -> span.children.flattened()
            is CookedSpan.Strike -> span.children.flattened()
            is CookedSpan.Underline -> span.children.flattened()
            is CookedSpan.Highlight -> span.children.flattened()
            is CookedSpan.Subscript -> span.children.flattened()
            is CookedSpan.Superscript -> span.children.flattened()
            is CookedSpan.Colored -> span.children.flattened()
            is CookedSpan.Sized -> span.children.flattened()
            is CookedSpan.Link -> span.children.flattened()
            is CookedSpan.Spoiler -> span.children.flattened()
            is CookedSpan.FootnoteRef -> span.content.flattened()
            else -> emptyList()
        }
    }
}
