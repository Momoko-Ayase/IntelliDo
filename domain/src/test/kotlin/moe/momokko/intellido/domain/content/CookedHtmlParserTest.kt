package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookedHtmlParserTest {
    private val parser = CookedHtmlParser()

    @Test
    fun `paragraphs strong and https links become native spans`() {
        val document = parser.parse(
            """<p>欢迎使用 <strong>IntelliDo</strong>。<a href="https://linux.do/">LINUX DO</a></p>""",
        )
        val paragraph = document.blocks.single() as CookedBlock.Paragraph
        assertEquals("欢迎使用 ", (paragraph.spans[0] as CookedSpan.Text).text)
        assertEquals("IntelliDo", ((paragraph.spans[1] as CookedSpan.Strong).children.single() as CookedSpan.Text).text)
        val link = paragraph.spans[3] as CookedSpan.Link
        assertEquals("https://linux.do/", link.href)
        assertEquals("LINUX DO", (link.children.single() as CookedSpan.Text).text)
        assertEquals("欢迎使用 IntelliDo。LINUX DO", document.plainText)
    }

    @Test
    fun `discourse data clicks stay beside the link without swallowing following text`() {
        val document = parser.parse(
            """<p>恰如去年<a href="https://linux.do/t/topic/188585" data-clicks="2.1k">向左还是向右的路线问题</a>）：野蛮生长。</p>""",
        )
        val paragraph = document.blocks.single() as CookedBlock.Paragraph

        assertEquals(4, paragraph.spans.size)
        assertEquals("恰如去年", (paragraph.spans[0] as CookedSpan.Text).text)
        val link = paragraph.spans[1] as CookedSpan.Link
        assertEquals("向左还是向右的路线问题", (link.children.single() as CookedSpan.Text).text)
        assertEquals("2.1k", (paragraph.spans[2] as CookedSpan.ClickCount).text)
        assertEquals("）：野蛮生长。", (paragraph.spans[3] as CookedSpan.Text).text)
    }

    @Test
    fun `lightbox wraps keep the original upload for open`() {
        val document = parser.parse(
            """
            <p>
              <a class="lightbox" href="https://linux.do/uploads/default/original/4X/7/5/c/75cdc.png">
                <img src="https://linux.do/uploads/default/optimized/4X/7/5/c/75cdc_2_690x270.png" alt="图">
              </a>
            </p>
            """.trimIndent(),
        )
        val image = document.blocks.filterIsInstance<CookedBlock.Image>().single()
        assertTrue(image.src.contains("/optimized/"), image.src)
        assertEquals("https://linux.do/uploads/default/original/4X/7/5/c/75cdc.png", image.original)
        val part = CookedHtml.nativeParts(document).filterIsInstance<CookedHtml.Part.Image>().single()
        assertEquals(image.original, part.originalSrc)
    }

    @Test
    fun `block-level code and hashtags join neighboring paragraphs`() {
        val document = parser.parse(
            """
            <p>插件对带 </p><code>抽奖</code><p> 标签的帖子生效，对 </p>
            <a class="hashtag-cooked" href="/tag/高级推广"><span>高级推广</span></a>
            <p> 标签帖子不生效。少于 </p><code>1</code><p> 帖，消耗 </p><code>24</code><p> 点积分。</p>
            """.trimIndent(),
        )
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        val text = paragraph.plainText().replace(Regex("\\s+"), " ").trim()
        assertEquals("插件对带 抽奖 标签的帖子生效，对 高级推广 标签帖子不生效。少于 1 帖，消耗 24 点积分。", text)
        assertTrue(paragraph.spans.any { it is CookedSpan.Code && it.text == "抽奖" })
        assertTrue(paragraph.spans.any { it is CookedSpan.Link })
        val html = CookedHtml.toSafeHtml(document)
        assertEquals(1, Regex("<p>").findAll(html).count(), html)
    }

    @Test
    fun `discourse list items keep hashtags and inline code in one line`() {
        val document = parser.parse(
            """
            <p>自本公告发布后，我们将正式启用该插件，并作如下设置:</p>
            <ul>
              <li>插件对带 <a class="hashtag-cooked" href="/tag/抽奖" data-type="tag" data-slug="抽奖"><span class="hashtag-icon-placeholder" data-slug="抽奖"></span><span>抽奖</span></a> 标签的帖子生效，对 <a class="hashtag-cooked" href="/tag/高级推广" data-type="tag" data-slug="高级推广"><span>高级推广</span></a> 标签帖子不生效。</li>
              <li>使用 <code>默认头像</code> 的用户，无法回复抽奖帖。</li>
              <li>用户现存主点少于 <code>1</code> 帖的，无法回复抽奖帖。</li>
              <li>每次回复抽奖帖，消耗 <code>24</code> 点积分。<strong>删帖不退。</strong></li>
            </ul>
            """.trimIndent(),
        )
        val list = document.blocks.filterIsInstance<CookedBlock.ListBlock>().single()
        assertFalse(list.ordered)
        assertEquals(4, list.items.size)
        val first = list.items[0].joinToString(" ") { it.plainText() }.replace(Regex("\\s+"), " ").trim()
        assertEquals("插件对带 抽奖 标签的帖子生效，对 高级推广 标签帖子不生效。", first)
        val third = list.items[2].joinToString(" ") { it.plainText() }.replace(Regex("\\s+"), " ").trim()
        assertEquals("用户现存主点少于 1 帖的，无法回复抽奖帖。", third)
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("•"), html)
        assertTrue(html.contains("&nbsp;"), html)
        assertTrue(html.contains("&nbsp;</a>"), html)
        assertTrue(html.contains("1"), html)
        assertFalse(html.contains("<li><p>"), html)
        assertTrue(html.contains("<font"), html)
    }

    @Test
    fun `pretty printed links and inline code stay in one paragraph without extra breaks`() {
        val document = parser.parse(
            """
            <p>见
            <a href="https://linux.do/t/101">话题</a>
            与
            <code>loadHomeTopics</code>
            即可。</p>
            """.trimIndent(),
        )
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        val link = paragraph.spans.filterIsInstance<CookedSpan.Link>().single()
        val code = paragraph.spans.filterIsInstance<CookedSpan.Code>().single()
        assertEquals("https://linux.do/t/101", link.href)
        assertEquals("话题", link.children.plainText())
        assertEquals("loadHomeTopics", code.text)
        assertTrue(paragraph.spans.none { it is CookedSpan.Break })
        assertTrue(paragraph.spans.none { it is CookedSpan.Text && it.text.contains('\n') })
        assertEquals("见 话题 与 loadHomeTopics 即可。", document.plainText.replace(Regex("\\s+"), " ").trim())
        val html = CookedHtml.toSafeHtml(document)
        assertFalse(html.contains("<br>"), html)
        assertTrue(html.contains("<a href="), html)
        assertTrue(html.contains("loadHomeTopics"), html)
        assertEquals(1, Regex("<p>").findAll(html).count())
    }

    @Test
    fun `explicit br stays a line break while surrounding text stays inline`() {
        val document = parser.parse("""<p>上一行<br>下一行 <code>x</code></p>""")
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        assertTrue(paragraph.spans.any { it is CookedSpan.Break })
        assertEquals("x", paragraph.spans.filterIsInstance<CookedSpan.Code>().single().text)
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("<br>"), html)
        assertTrue(html.contains("下一行"), html)
        assertEquals(1, Regex("<p>").findAll(html).count())
    }

    @Test
    fun `scripts javascript urls and event handlers never become executable nodes`() {
        val document = parser.parse(
            """<p onclick="alert(1)"><script>alert(1)</script><a href="javascript:alert(1)">x</a></p>""",
        )
        val text = document.plainText
        assertFalse(text.contains("alert"))
        assertTrue(document.blocks.none { it is CookedBlock.Unknown && it.tag == "script" })
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        assertTrue(paragraph.spans.none { it is CookedSpan.Link && it.href.startsWith("javascript") })
    }

    @Test
    fun `unknown tags become native error placeholders`() {
        val document = parser.parse("""<p>ok</p><widget data-x="1">nope</widget>""")
        assertTrue(document.blocks.any { it is CookedBlock.Paragraph })
        val unknown = document.blocks.filterIsInstance<CookedBlock.Unknown>().single()
        assertEquals("widget", unknown.tag)
    }

    @Test
    fun `lists quotes and fenced code stay structured`() {
        val document = parser.parse(
            """
            <blockquote><p>引用</p></blockquote>
            <ul><li>一项</li><li>二项</li></ul>
            <pre><code class="lang-kotlin">fun main() {}</code></pre>
            """.trimIndent(),
        )
        assertTrue(document.blocks[0] is CookedBlock.Quote)
        val list = document.blocks.filterIsInstance<CookedBlock.ListBlock>().single()
        assertFalse(list.ordered)
        assertEquals(2, list.items.size)
        val code = document.blocks.filterIsInstance<CookedBlock.CodeBlock>().single()
        assertEquals("kotlin", code.language)
        assertEquals("fun main() {}", code.code)
    }

    @Test
    fun `linux do images are trusted and other images stay as alt text`() {
        val document = parser.parse(
            """
            <p><img src="https://linux.do/uploads/a.png" alt="站内">x<img src="https://example.com/track.png" alt="站外"></p>
            """.trimIndent(),
        )
        val images = document.blocks.filterIsInstance<CookedBlock.Image>()
        assertEquals(2, images.size)
        assertTrue(images[0].trusted)
        assertEquals("https://linux.do/uploads/a.png", images[0].src)
        assertFalse(images[1].trusted)
        assertEquals("https://example.com/track.png", images[1].src)
        val html = CookedHtml.toSafeHtml(document)
        assertFalse(html.contains("https://linux.do/uploads/a.png"))
        assertFalse(html.contains("https://example.com/track.png"))
        assertTrue(html.contains("站内"))
        assertTrue(html.contains("站外"))
    }

    @Test
    fun `discourse aside quotes keep attribution and quoted body`() {
        val document = parser.parse(
            """
            <aside class="quote" data-post="2" data-topic="101" data-username="helper">
              <div class="title">
                <div class="quote-controls"></div>
                <img alt="" width="24" height="24" src="https://linux.do/user_avatar/linux.do/helper/24/1_2.png" class="avatar"> helper:
              </div>
              <blockquote>
                <p>Home 是永久标签页。</p>
              </blockquote>
            </aside>
            """.trimIndent(),
        )
        val quote = document.blocks.filterIsInstance<CookedBlock.Quote>().single()
        assertEquals("helper", quote.attribution)
        assertEquals("Home 是永久标签页。", quote.children.joinToString(" ") { it.plainText() }.trim())
        assertTrue(document.blocks.none { it is CookedBlock.Unknown })
    }

    @Test
    fun `emoji images become unicode glyphs instead of remote img tags`() {
        val document = parser.parse(
            """<p>好 <img src="https://linux.do/images/emoji/twitter/tada.png?v=12" title=":tada:" class="emoji" alt=":tada:"> 呀</p>""",
        )
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        val emoji = paragraph.spans.filterIsInstance<CookedSpan.Emoji>().single()
        assertEquals("tada", emoji.shortcode)
        assertEquals("🎉", emoji.glyph)
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("🎉"))
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("linux.do/images/emoji"))
        assertEquals(
            listOf("https://linux.do/images/emoji/twitter/tada.png?v=12"),
            CookedHtml.emojiUrls(document),
        )
        val painted = CookedHtml.toSafeHtml(
            document,
            mapOf("https://linux.do/images/emoji/twitter/tada.png?v=12" to "intellido-media:tada"),
        )
        assertTrue(painted.contains("<img"), painted)
        assertTrue(painted.contains("intellido-media:tada"), painted)
        assertFalse(painted.contains("linux.do/images/emoji"), painted)
    }

    @Test
    fun `custom emoji keeps a trusted src so native HTML can paint the image`() {
        val document = parser.parse(
            """<p>嗨 <img src="https://cdn.ldstatic.com/original/3X/1/a/1a9f6c30.png" class="emoji emoji-custom" alt=":tieba_025:"> 呀</p>""",
        )
        val emoji = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
            .spans.filterIsInstance<CookedSpan.Emoji>().single()
        assertEquals("tieba_025", emoji.shortcode)
        assertEquals("", emoji.glyph)
        assertEquals("https://cdn.ldstatic.com/original/3X/1/a/1a9f6c30.png", emoji.src)
        assertEquals(listOf(emoji.src), CookedHtml.emojiUrls(document))
        val pending = CookedHtml.toSafeHtml(document)
        assertTrue(pending.contains(":tieba_025:"), pending)
        assertFalse(pending.contains("<img"))
        val html = CookedHtml.toSafeHtml(document, mapOf(emoji.src to "data:image/png;base64,abc"))
        assertTrue(html.contains("<img"), html)
        assertTrue(html.contains("data:image/png;base64,abc"), html)
        assertFalse(html.contains("cdn.ldstatic.com"), html)
    }

    @Test
    fun `lightbox wrappers and relative uploads become trusted image blocks`() {
        val document = parser.parse(
            """
            <p>
              <div class="lightbox-wrapper">
                <a class="lightbox" href="https://linux.do/uploads/default/original/1X/photo.png">
                  <img src="/uploads/default/optimized/1X/photo.png" alt="附图" width="690" height="200">
                  <div class="meta">photo.png</div>
                </a>
              </div>
            </p>
            """.trimIndent(),
        )
        val image = document.blocks.filterIsInstance<CookedBlock.Image>().single()
        assertEquals("https://linux.do/uploads/default/optimized/1X/photo.png", image.src)
        assertTrue(image.trusted)
        assertEquals("附图", image.alt)
    }

    @Test
    fun `protocol relative linux do uploads are trusted`() {
        val document = parser.parse(
            """<p><img src="//linux.do/uploads/default/original/1X/a.png" alt="站内"></p>""",
        )
        val image = document.blocks.filterIsInstance<CookedBlock.Image>().single()
        assertEquals("https://linux.do/uploads/default/original/1X/a.png", image.src)
        assertTrue(image.trusted)
    }

    @Test
    fun `ldstatic cdn uploads are trusted`() {
        val document = parser.parse(
            """<p><img src="https://cdn3.ldstatic.com/optimized/4X/6/b/f/photo.png" alt="图"></p>""",
        )
        val image = document.blocks.filterIsInstance<CookedBlock.Image>().single()
        assertTrue(image.trusted)
        assertEquals("https://cdn3.ldstatic.com/optimized/4X/6/b/f/photo.png", image.src)
    }

    @Test
    fun `relative links are kept as inlines and later absolutized`() {
        val document = parser.parse("""<p>见 <a href="/t/101">话题</a></p>""")
        val paragraph = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single()
        val link = paragraph.spans.filterIsInstance<CookedSpan.Link>().single()
        assertEquals("/t/101", link.href)
        assertTrue(CookedHtml.toSafeHtml(document).contains("https://linux.do/t/101"))
    }

    @Test
    fun `markdown tables keep header and body cells`() {
        val document = parser.parse(
            """
            <div class="md-table">
              <table>
                <thead><tr><th>名称</th><th>值</th></tr></thead>
                <tbody><tr><td>foo</td><td><code>bar</code></td></tr></tbody>
              </table>
            </div>
            """.trimIndent(),
        )
        val table = document.blocks.filterIsInstance<CookedBlock.Table>().single()
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[0].header)
        assertEquals("名称", table.rows[0].cells[0].spans.plainText())
        assertEquals("foo", table.rows[1].cells[0].spans.plainText())
        assertEquals("bar", (table.rows[1].cells[1].spans.single() as CookedSpan.Code).text)
        assertTrue(document.blocks.none { it is CookedBlock.Paragraph && it.plainText() == "名称" })
    }

    @Test
    fun `mentions hashtags strike kbd and mark stay structured`() {
        val document = parser.parse(
            """
            <p>
              <a class="mention" href="/u/helper">@helper</a>
              <a class="hashtag" href="/tag/faq">#faq</a>
              <s>旧</s><u>下划线</u><kbd>Ctrl</kbd><sup>2</sup><sub>n</sub>
              <mark>高亮</mark>
            </p>
            """.trimIndent(),
        )
        val spans = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single().spans
        val mention = spans.filterIsInstance<CookedSpan.Link>().first { it.kind == CookedSpan.Link.Kind.Mention }
        assertEquals("/u/helper", mention.href)
        assertEquals("@helper", mention.children.plainText())
        val hashtag = spans.filterIsInstance<CookedSpan.Link>().first { it.kind == CookedSpan.Link.Kind.Hashtag }
        assertEquals("/tag/faq", hashtag.href)
        assertTrue(spans.any { it is CookedSpan.Strike && it.children.plainText() == "旧" })
        assertTrue(spans.any { it is CookedSpan.Underline && it.children.plainText() == "下划线" })
        assertTrue(spans.any { it is CookedSpan.Kbd && it.text == "Ctrl" })
        assertTrue(spans.any { it is CookedSpan.Superscript && it.children.plainText() == "2" })
        assertTrue(spans.any { it is CookedSpan.Subscript && it.children.plainText() == "n" })
        assertTrue(spans.any { it is CookedSpan.Highlight && it.children.plainText() == "高亮" })
    }

    @Test
    fun `allowlisted hex colors are kept and style urls are dropped`() {
        val document = parser.parse(
            """<p><span style="color:#ff0000">红</span><span style="color:url(javascript:alert(1))">坏</span></p>""",
        )
        val spans = document.blocks.filterIsInstance<CookedBlock.Paragraph>().single().spans
        val colored = spans.filterIsInstance<CookedSpan.Colored>().single()
        assertEquals("#ff0000", colored.color)
        assertEquals("红", colored.children.plainText())
        assertTrue(spans.any { it is CookedSpan.Text && it.text.contains("坏") })
        assertFalse(document.plainText.contains("javascript"))
    }

    @Test
    fun `oneboxes keep title description and url without remote thumbnails`() {
        val document = parser.parse(
            """
            <aside class="onebox allowlistedgeneric" data-onebox-src="https://github.com/momokko/intellido">
              <header class="source">
                <img src="https://github.com/favicon.ico" class="site-icon">
                <a href="https://github.com/momokko/intellido">github.com</a>
              </header>
              <article class="onebox-body">
                <img src="https://opengraph.githubassets.com/x.png" class="thumbnail" alt="og">
                <h3><a href="https://github.com/momokko/intellido">IntelliDo</a></h3>
                <p>Unofficial LINUX DO client.</p>
              </article>
            </aside>
            """.trimIndent(),
        )
        val onebox = document.blocks.filterIsInstance<CookedBlock.Onebox>().single()
        assertEquals("https://github.com/momokko/intellido", onebox.url)
        assertEquals("IntelliDo", onebox.title)
        assertTrue(onebox.description.contains("Unofficial"))
        assertEquals("github.com", onebox.site)
        assertTrue(document.blocks.none { it is CookedBlock.Image })
        val html = CookedHtml.toSafeHtml(document)
        assertFalse(html.contains("opengraph.githubassets.com"))
        assertFalse(html.contains("favicon.ico"))
        assertTrue(html.contains("onebox"))
    }

    @Test
    fun `details and spoilers hide body from plain text`() {
        val document = parser.parse(
            """
            <details>
              <summary>展开说明</summary>
              <p>里面的话</p>
            </details>
            <div class="spoiler blurred"><p>剧透答案</p></div>
            <p>可见 <span class="spoiled">隐藏字</span> 结束</p>
            """.trimIndent(),
        )
        val details = document.blocks.filterIsInstance<CookedBlock.Details>().single()
        assertEquals("展开说明", details.summary)
        assertEquals("里面的话", details.children.joinToString(" ") { it.plainText() }.trim())
        val spoiler = document.blocks.filterIsInstance<CookedBlock.Spoiler>().single()
        assertTrue(spoiler.children.joinToString { it.plainText() }.contains("剧透答案"))
        assertFalse(document.plainText.contains("里面的话"))
        assertFalse(document.plainText.contains("剧透答案"))
        assertFalse(document.plainText.contains("隐藏字"))
        val html = CookedHtml.toSafeHtml(document)
        assertFalse(html.contains("里面的话"))
        assertFalse(html.contains("剧透答案"))
        assertFalse(html.contains("隐藏字"))
    }

    @Test
    fun `polls collect option labels and ignore vote buttons`() {
        val document = parser.parse(
            """
            <div class="poll" data-poll-name="color" data-poll-type="regular">
              <div>
                <div class="poll-container">
                  <ul>
                    <li data-poll-option-id="a">红</li>
                    <li data-poll-option-id="b">蓝</li>
                  </ul>
                </div>
                <div class="poll-buttons"><button>Vote now!</button></div>
              </div>
            </div>
            """.trimIndent(),
        )
        val poll = document.blocks.filterIsInstance<CookedBlock.Poll>().single()
        assertEquals("color", poll.name)
        assertEquals(listOf("红", "蓝"), poll.options)
        assertFalse(poll.options.any { it.contains("Vote") })
    }

    @Test
    fun `ordered lists keep a start index`() {
        val document = parser.parse("""<ol start="3"><li>第三</li><li>第四</li></ol>""")
        val list = document.blocks.filterIsInstance<CookedBlock.ListBlock>().single()
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals("第三", list.items[0].joinToString { it.plainText() })
    }

    @Test
    fun `javascript onebox sources never become links`() {
        val document = parser.parse(
            """
            <aside class="onebox" data-onebox-src="javascript:alert(1)">
              <h3><a href="javascript:alert(1)">bad</a></h3>
              <p>nope</p>
            </aside>
            """.trimIndent(),
        )
        val html = CookedHtml.toSafeHtml(document)
        assertFalse(html.contains("javascript"))
        val onebox = document.blocks.filterIsInstance<CookedBlock.Onebox>().singleOrNull()
        if (onebox != null) {
            assertFalse(onebox.url.startsWith("javascript"))
        }
    }
}
