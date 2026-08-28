package moe.momokko.intellido.ui.content

import moe.momokko.intellido.domain.content.CookedHtml
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.transport.DiscourseFormatPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JViewport

class PostBodyPaneTest {
    @Test
    fun `local discourse format preview renders every post at a narrow reading width`() {
        DiscourseFormatPreview.posts.forEach { post ->
            val pane = PostBodyPane(post.cookedHtml)
            pane.size = Dimension(360, 6000)
            pane.doLayout()

            assertTrue(pane.visibleText().isNotBlank(), "post ${post.postNumber} rendered blank")
            assertTrue(pane.preferredSize.height > 80, "post ${post.postNumber} height=${pane.preferredSize.height}")
            pane.lineAdvances().forEach { advance ->
                assertTrue(advance <= 380, "post ${post.postNumber} escaped width: $advance\n${pane.dumpLayout()}")
            }
        }
    }

    @Test
    fun `inline code hashtags and links do not become their own lines`() {
        val html = CookedHtml.toSafeHtml(
            CookedHtmlParser().parse(
                """
                <p>插件对带 <code>抽奖</code> 标签的帖子生效，对 <code>高级推广</code> 标签帖子不生效。
                用户现存主点少于 <code>1</code> 帖的，每次消耗 <code>24</code> 点积分。
                带 <a class="hashtag-cooked" href="/tag/抽奖"><span>抽奖</span></a> 标签。
                具体见：<a href="https://linux.do/leaderboard">https://linux.do/leaderboard</a>。</p>
                """.trimIndent(),
            ),
        )
        val pane = PostBodyPane(html)
        pane.size = Dimension(960, 640)
        pane.doLayout()
        val raw = pane.layoutText()
        val dump = pane.dumpLayout()
        val one = raw.indexOf('1')
        val around = raw.indexOf("少于")
        assertTrue(one >= 0 && around >= 0, raw)
        val yCode = pane.charBox(one)?.y
        val yText = pane.charBox(around)?.y
        assertTrue(yCode != null && yText != null, dump)
        assertEquals(yText!!, yCode!!, 1.0, "code y=$yCode text y=$yText dump=\n$dump\nraw=$raw")
        assertFalse(raw.contains("\n1\n"), raw)
        val text = pane.visibleText().replace(' ', ' ').replace(Regex("\\s+"), " ")
        assertTrue(text.contains("少于 1 帖") || text.contains("少于1帖"), "$text\n$dump")
        assertTrue(text.contains("leaderboard"), text)
    }

    @Test
    fun `list items keep hashtags and code on the same line`() {
        val html = CookedHtml.toSafeHtml(
            CookedHtmlParser().parse(
                """
                <ul>
                  <li>使用 <code>默认头像</code> 的用户，无法回复抽奖帖。</li>
                  <li>用户现存主点少于 <code>1</code> 帖的，无法回复抽奖帖。</li>
                </ul>
                """.trimIndent(),
            ),
        )
        val pane = PostBodyPane(html)
        pane.size = Dimension(960, 400)
        pane.doLayout()
        val raw = pane.layoutText()
        val i = raw.indexOf('1')
        val around = raw.indexOf("少于")
        assertTrue(i >= 0 && around >= 0, raw)
        val yAround = pane.charBox(around)?.y
        val yCode = pane.charBox(i)?.y
        assertTrue(yAround != null && yCode != null, raw)
        assertEquals(yAround!!, yCode!!, 1.0, raw)
        val text = pane.visibleText().replace(' ', ' ').replace(Regex("\\s+"), " ")
        assertTrue(text.contains("默认头像"), text)
        assertTrue(text.contains("少于 1 帖") || text.contains("少于1帖"), text)
        assertTrue(html.contains("•"), html)
        assertFalse(html.contains("<li><p>"), html)
    }

    @Test
    fun `linux do links are offered to the host before the system browser`() {
        val seen = mutableListOf<String>()
        val pane = PostBodyPane(
            """<p><a href="https://linux.do/t/101">话题</a></p>""",
            onNavigate = { url -> seen += url; true },
        )
        pane.size = Dimension(400, 80)
        pane.doLayout()
        assertTrue(pane.clickText("话题"), pane.layoutText())
        assertEquals(listOf("https://linux.do/t/101"), seen)
    }

    @Test
    fun `hashtag icons stay on the same line as surrounding text`() {
        val html = CookedHtml.toSafeHtml(
            CookedHtmlParser().parse(
                """<p>插件对带 <a class="hashtag-cooked" href="/tag/抽奖"><span>抽奖</span></a> 标签的帖子生效。</p>""",
            ),
        )
        assertTrue(html.contains("chip:抽奖"), html)
        assertTrue(html.contains("hashtag-chip"), html)
        val pane = PostBodyPane(html)
        pane.size = Dimension(960, 400)
        pane.doLayout()
        val raw = pane.layoutText()
        val dump = pane.dumpLayout()
        val around = raw.indexOf("插件")
        val after = raw.indexOf("标签")
        assertTrue(around >= 0 && after >= 0, "$raw\n$dump")
        assertTrue(pane.inlineImageCount() >= 1, dump)
        val yAround = pane.charBox(around)?.y
        val yAfter = pane.charBox(after)?.y
        assertTrue(yAround != null && yAfter != null, "$raw\n$dump")
        assertEquals(
            yAround!!,
            yAfter!!,
            1.0,
            "hashtag y dump=\n$dump\nraw=$raw",
        )
        val image = InlineMedia.image("intellido-media:chip:抽奖")
        assertTrue(image != null, "chip image should be registered")
        val userH = com.intellij.util.ui.ImageUtil.getUserHeight(image!!)
        val expected = com.intellij.util.ui.JBUI.scale(HashtagChips.HEIGHT)
        assertTrue(
            userH in (expected - 2)..(expected + 2),
            "chip user height=$userH expected~$expected raw=${image.getHeight(null)}",
        )
    }

    @Test
    fun `reading line height stretches wrapped post body`() {
        val html = "<p>${"阅读行距测试文字内容".repeat(24)}</p>"
        val previous = moe.momokko.intellido.platform.reading.ReadingAppearance.current
        try {
            moe.momokko.intellido.platform.reading.ReadingAppearance.replace(
                moe.momokko.intellido.platform.reading.ReadingStyle(15, 1.5f, 0),
            )
            val tightPane = PostBodyPane(html)
            tightPane.size = Dimension(420, 1200)
            tightPane.doLayout()
            val tight = tightPane.preferredSize.height
            moe.momokko.intellido.platform.reading.ReadingAppearance.replace(
                moe.momokko.intellido.platform.reading.ReadingStyle(15, 2.4f, 0),
            )
            val loosePane = PostBodyPane(html)
            loosePane.size = Dimension(420, 1200)
            loosePane.doLayout()
            val loose = loosePane.preferredSize.height
            val dump = loosePane.dumpLayout()
            assertTrue(loose > tight + 12, "tight=$tight loose=$loose dump=\n$dump")
        } finally {
            moe.momokko.intellido.platform.reading.ReadingAppearance.replace(previous)
        }
    }

    @Test
    fun `long chinese paragraph wraps inside the pane instead of clipping`() {
        val sentence =
            "有人在亮眼的数据面前逐渐疯狂，而我们应该在数据面前冷静。我们需要思考的是，如何让每一次的数据不成为顶峰，而只作为新的起点。未来，才是我们需要聚焦关注的。"
        val html = "<p>$sentence</p>"
        val pane = PostBodyPane(html)
        pane.size = Dimension(420, 2000)
        pane.doLayout()
        pane.preferredSize.also { pane.size = Dimension(420, it.height.coerceAtLeast(1)) }
        pane.doLayout()
        val dump = pane.dumpLayout()
        val advances = pane.lineAdvances()
        assertTrue(advances.size >= 3, "expected wrapped rows, dump=\n$dump")
        advances.forEach { advance ->
            assertTrue(advance <= 420 + 24, "row wider than pane: $advance dump=\n$dump")
        }
        val raw = pane.visibleText()
        assertTrue(raw.replace(Regex("\\s+"), "").contains("聚焦关注的"), raw)
        assertFalse(raw.contains("思\n考"), raw)
        val lastContent = pane.layoutText().indexOfLast { !it.isWhitespace() && it != '\n' }
        val firstBox = pane.charBox(1)
        val lastBox = pane.charBox(lastContent.coerceAtLeast(1))
        assertTrue(lastBox != null && firstBox != null, dump)
        assertTrue(
            lastBox!!.y > firstBox!!.y + 8,
            "CJK paragraph stayed on one line: first=$firstBox last=$lastBox dump=\n$dump",
        )
    }

    @Test
    fun `wrapped host is taller than a single line at a narrow width`() {
        val sentence =
            "有人在亮眼的数据面前逐渐疯狂，而我们应该在数据面前冷静。我们需要思考的是，如何让每一次的数据不成为顶峰，而只作为新的起点。未来，才是我们需要聚焦关注的。"
        val pane = PostBodyPane("<p>$sentence$sentence</p>")
        val host = pane.wrapped()
        host.setSize(400, 1200)
        host.doLayout()
        assertTrue(host.preferredSize.height > 48, "height=${host.preferredSize.height}")
        val raw = pane.visibleText()
        assertFalse(raw.contains("思\n考"), raw)
        assertTrue(raw.replace(Regex("\\s+"), "").contains("聚焦关注的"), raw)
        pane.lineAdvances().forEach { advance ->
            assertTrue(advance <= 400 + 24, "advance=$advance height=${host.preferredSize.height}")
        }
    }

    @Test
    fun `paragraph follows the viewport when an ancestor still has its old width`() {
        val sentence =
            "有人在亮眼的数据面前逐渐疯狂，而我们应该在数据面前冷静。我们需要思考的是，如何让每一次的数据不成为顶峰，而只作为新的起点。未来，才是我们需要聚焦关注的。"
        val pane = PostBodyPane("<p>$sentence$sentence</p>")
        val staleWideHost = JPanel(null).apply {
            add(pane)
            size = Dimension(900, 2000)
            preferredSize = size
        }
        pane.setBounds(0, 0, 900, 1800)
        val viewport = JViewport().apply {
            extentSize = Dimension(360, 900)
            view = staleWideHost
            viewSize = Dimension(900, 2000)
        }

        pane.doLayout()

        val advances = pane.lineAdvances()
        assertTrue(advances.size >= 6, "expected viewport wrapping, dump=\n${pane.dumpLayout()}")
        advances.forEach { advance ->
            assertTrue(advance <= 380, "line escaped viewport: $advance\n${pane.dumpLayout()}")
        }
        assertEquals(360, pane.visibleRect.width)
    }

    @Test
    fun `closed folds and inline spoilers do not leak into copy text`() {
        val pane = PostBodyPane(
            """
            <details><summary>展开说明</summary><p>折叠秘密</p></details>
            <div class="spoiler"><p>块级秘密</p></div>
            <p>可见文字 <span class="spoiled">行内秘密</span> 结束</p>
            """.trimIndent(),
        )
        pane.size = Dimension(420, 600)
        pane.doLayout()

        val closed = pane.visibleText()
        assertTrue(closed.contains("可见文字"), closed)
        assertFalse(closed.contains("折叠秘密"), closed)
        assertFalse(closed.contains("块级秘密"), closed)
        assertFalse(closed.contains("行内秘密"), closed)
        assertTrue(pane.layoutText().contains("●"), pane.layoutText())

        assertTrue(pane.clickText("●"), pane.dumpLayout())
        val revealed = pane.visibleText()
        assertTrue(revealed.contains("行内秘密"), revealed)
        assertFalse(revealed.contains("折叠秘密"), revealed)
        assertFalse(revealed.contains("块级秘密"), revealed)
    }

    @Test
    fun `structured discourse blocks remain readable at a narrow width`() {
        val document = CookedHtmlParser().parse(
            """
            <h2 style="text-align:center">格式预览</h2>
            <blockquote><p>[!warning] 注意<br>这是需要换行显示的较长提醒内容。</p></blockquote>
            <table><thead><tr><th>名称</th><th>说明</th></tr></thead><tbody><tr><td>原生表格</td><td>窄窗口中也应该自动折行而不是裁切</td></tr></tbody></table>
            <dl><dt>术语</dt><dd><p>带有缩进的解释文字</p></dd></dl>
            <div class="poll" data-poll-question="选择支持的方案" data-poll-type="multiple"><ul><li data-poll-option-id="1">一个非常长的投票选项，需要在窄窗口中换行</li></ul></div>
            <div class="math">x = \\frac{-b}{2a}</div>
            <svg viewBox="0 0 20 10"><rect width="20" height="10" fill="#4c8bf5"/></svg>
            """.trimIndent(),
        )
        val pane = PostBodyPane(document)
        pane.size = Dimension(360, 2000)
        pane.doLayout()

        val text = pane.visibleText().replace(Regex("\\s+"), " ")
        assertTrue(text.contains("格式预览"), text)
        assertTrue(text.contains("自动折行"), text)
        assertTrue(text.contains("带有缩进"), text)
        assertTrue(text.contains("投票选项"), text)
        assertTrue(text.contains("frac"), text)
        assertTrue(pane.preferredSize.height > 180, "height=${pane.preferredSize.height}\n${pane.dumpLayout()}")
        pane.lineAdvances().forEach { advance ->
            assertTrue(advance <= 380, "advance=$advance\n${pane.dumpLayout()}")
        }
    }
}
