package moe.momokko.intellido.domain.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookedHtmlTest {
    @Test
    fun `safe html keeps emphasis and drops unknown widgets`() {
        val document = CookedHtmlParser().parse(
            """<p>欢迎 <strong>IntelliDo</strong></p><widget>x</widget><pre><code>read-only</code></pre>""",
        )
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("<b>IntelliDo</b>"))
        assertTrue(html.contains("<pre><code>read-only</code></pre>"))
        assertFalse(html.contains("widget"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun `inline code and links stay inside the same paragraph tag`() {
        val document = CookedHtmlParser().parse(
            """<p>打开 <a href="https://linux.do/t/101">话题</a> 看 <code>IntelliDoRuntime</code>。</p>""",
        )
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.startsWith("<p>"), html)
        assertTrue(html.endsWith("</p>"), html)
        assertFalse(html.contains("<br>"), html)
        assertTrue(html.contains("<a href=\"https://linux.do/t/101\">"), html)
        assertTrue(html.contains("IntelliDoRuntime"), html)
        assertFalse(html.contains("<code>IntelliDoRuntime</code>"), html)
        assertFalse(html.contains("<tt>IntelliDoRuntime</tt>"), html)
        assertTrue(html.contains("<font"), html)
        assertTrue(html.contains("&nbsp;"), html)
        assertEquals(1, Regex("<p>").findAll(html).count())
    }

    @Test
    fun `hashtag links include the LINUX DO tag icon next to the name`() {
        val document = CookedHtmlParser().parse(
            """<p>带 <a class="hashtag-cooked" href="/tag/抽奖" data-type="tag"><span class="hashtag-icon-placeholder"></span><span>抽奖</span></a> 和 <a class="hashtag-cooked" href="/tag/高级推广"><span>高级推广</span></a>。</p>""",
        )
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("intellido-media:chip:抽奖"), html)
        assertTrue(html.contains("intellido-media:chip:高级推广"), html)
        assertTrue(html.contains("class=\"hashtag-chip\""), html)
        assertTrue(html.contains("抽奖"), html)
        assertTrue(html.contains("高级推广"), html)
        assertEquals(1, Regex("<p>").findAll(html).count(), html)
        assertFalse(html.contains("<div"), html)
    }

    @Test
    fun `quotes render as blockquote and never emit remote image urls`() {
        val document = CookedHtmlParser().parse(
            """
            <aside class="quote" data-username="helper"><blockquote><p>引用</p></blockquote></aside>
            <p><img src="https://linux.do/uploads/a.png" alt="图"><img src="https://evil.example/x.png" alt="追踪"></p>
            """.trimIndent(),
        )
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("helper"))
        assertTrue(html.contains("引用"))
        assertTrue(html.contains("图"))
        assertTrue(html.contains("追踪"))
        assertFalse(html.contains("https://linux.do/uploads/a.png"))
        assertFalse(html.contains("evil.example"))
        assertFalse(html.contains("<img"))
    }

    @Test
    fun `generic image alts are omitted`() {
        val document = CookedHtmlParser().parse(
            """<p>前</p><p><img src="https://linux.do/uploads/a.png" alt="image" width="690" height="400"></p><p>后</p>""",
        )
        val html = CookedHtml.toSafeHtml(document)
        assertEquals("<p>前</p><p>后</p>", html)
        assertFalse(html.contains("image"))
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("height"))
    }

    @Test
    fun `jcef data uris become native img tags for trusted sources only`() {
        val document = CookedHtmlParser().parse(
            """<p><img src="https://linux.do/uploads/a.png" alt="站内"><img src="https://evil.example/x.png" alt="追踪"></p>""",
        )
        val media = mapOf(
            "https://linux.do/uploads/a.png" to "data:image/jpeg;base64,abc",
            "https://evil.example/x.png" to "data:image/jpeg;base64,nope",
        )
        val html = CookedHtml.toSafeHtml(document, media)
        assertTrue(html.contains("data:image/jpeg;base64,abc"))
        assertTrue(html.contains("<img"))
        assertFalse(html.contains("evil.example"))
        assertFalse(html.contains("nope"))
        assertEquals(listOf("https://linux.do/uploads/a.png"), CookedHtml.trustedMediaUrls(document))
    }

    @Test
    fun `trusted images become native parts so html does not carry data uris`() {
        val document = CookedHtmlParser().parse(
            """<p>前</p><p><img src="https://linux.do/uploads/a.png" alt="图"></p><p>后</p>""",
        )
        val parts = CookedHtml.nativeParts(document)
        assertEquals(3, parts.size)
        assertTrue(parts[0] is CookedHtml.Part.Html)
        val image = parts[1] as CookedHtml.Part.Image
        assertEquals("https://linux.do/uploads/a.png", image.src)
        assertTrue(parts[2] is CookedHtml.Part.Html)
    }

    @Test
    fun `tables oneboxes and mentions render as native html without remote media`() {
        val document = CookedHtmlParser().parse(
            """
            <p><a class="mention" href="/u/helper">@helper</a> 看表</p>
            <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>
            <aside class="onebox" data-onebox-src="https://linux.do/t/101">
              <h3>话题</h3><p>摘要</p>
              <img src="https://evil.example/og.png" alt="og">
            </aside>
            <p><s>旧</s> <kbd>Esc</kbd></p>
            """.trimIndent(),
        )
        val html = CookedHtml.toSafeHtml(document)
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<th>A</th>"))
        assertTrue(html.contains("@helper"))
        assertTrue(html.contains("class=\"quote-title\"") || html.contains("@helper"))
        assertTrue(html.contains("class=\"onebox\""))
        assertTrue(html.contains("<s>旧</s>") || html.contains("<strike>旧</strike>"))
        assertTrue(html.contains("Esc"))
        assertFalse(html.contains("<kbd>"))
        assertTrue(html.contains("<font"))
        assertTrue(html.contains("https://linux.do/u/helper"))
        assertFalse(html.contains("evil.example"))
        assertFalse(html.contains("<img"))
    }

    @Test
    fun `native parts split code details spoilers and polls from wrapping html`() {
        val document = CookedHtmlParser().parse(
            """
            <p>前</p>
            <pre><code class="lang-kotlin">fun main() {}</code></pre>
            <details><summary>更多</summary><p>藏</p></details>
            <div class="spoiler"><p>密</p></div>
            <div class="poll" data-poll-name="poll"><ul><li data-poll-option-id="1">甲</li></ul></div>
            <p>后</p>
            """.trimIndent(),
        )
        val parts = CookedHtml.nativeParts(document)
        assertTrue(parts.first() is CookedHtml.Part.Html)
        assertEquals("fun main() {}", (parts.filterIsInstance<CookedHtml.Part.Code>().single()).code)
        assertEquals("更多", parts.filterIsInstance<CookedHtml.Part.Details>().single().summary)
        assertTrue(parts.any { it is CookedHtml.Part.Spoiler })
        assertEquals(listOf("甲"), parts.filterIsInstance<CookedHtml.Part.Poll>().single().options)
        assertTrue(parts.last() is CookedHtml.Part.Html)
    }

    @Test
    fun `raw cooked html still yields linux do upload urls`() {
        val html = """<div class="cooked"><img src="https://linux.do/uploads/default/original/1X/a.png" alt=""></div>"""
        assertEquals(
            listOf("https://linux.do/uploads/default/original/1X/a.png"),
            CookedHtml.uploadUrls(html),
        )
        assertEquals(
            listOf("https://linux.do/uploads/default/original/1X/b.png"),
            CookedHtml.uploadUrls("""<img src="/uploads/default/original/1X/b.png">"""),
        )
        assertEquals(
            listOf("https://cdn3.ldstatic.com/optimized/4X/6/b/f/photo.png"),
            CookedHtml.uploadUrls("""<img src="https://cdn3.ldstatic.com/optimized/4X/6/b/f/photo.png">"""),
        )
        assertEquals(
            emptyList<String>(),
            CookedHtml.uploadUrls(
                """<img src="https://cdn.ldstatic.com/original/1X/tieba.png" class="emoji emoji-custom" alt=":tieba_025:">""",
            ),
        )
    }

    @Test
    fun `quotes in community text cannot break out of an attribute`() {
        val document = CookedHtmlParser().parse(
            """<p><a href="https://linux.do/t/1">a&quot; onx=&quot;y</a></p>""",
        )
        val html = CookedHtml.toSafeHtml(document)
        // The link text is attribute-injection bait; it must land escaped as text.
        assertTrue(html.contains("&quot;"), html)
        assertFalse(html.contains("""onx="y"""), html)
    }

    @Test
    fun `a crafted image alt cannot inject a second src`() {
        val src = "https://linux.do/uploads/original/1X/a.png"
        val document = CookedHtmlParser().parse(
            """<img src="$src" alt="x&quot; src=&quot;http://evil.example/px.png">""",
        )
        val html = CookedHtml.toSafeHtml(document, mapOf(src to "intellido-media:$src"))
        assertFalse(html.contains("""src="http://evil.example"""), html)
        assertTrue(html.contains("&quot;"), html)
    }
}
